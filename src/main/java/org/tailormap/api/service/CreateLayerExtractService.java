/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.service;

import ch.rasc.sse.eventbus.SseEvent;
import ch.rasc.sse.eventbus.SseEventBus;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.geotools.api.data.FeatureEvent;
import org.geotools.api.data.FileDataStore;
import org.geotools.api.data.Query;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.data.SimpleFeatureStore;
import org.geotools.api.data.Transaction;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.filter.Filter;
import org.geotools.api.filter.FilterFactory;
import org.geotools.api.filter.sort.SortOrder;
import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.csv.CSVDataStoreFactory;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.SchemaException;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.filter.text.ecql.ECQL;
import org.geotools.util.factory.GeoTools;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tailormap.api.controller.LayerExtractController;
import org.tailormap.api.geotools.data.excel.ExcelDataStoreFactory;
import org.tailormap.api.geotools.featuresources.FeatureSourceFactoryHelper;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.util.UUIDv7;
import org.tailormap.api.viewer.model.ServerSentEventResponse;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Service
public class CreateLayerExtractService {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final SseEventBus eventBus;
  private final JsonMapper jsonMapper;
  private final FeatureSourceFactoryHelper featureSourceFactoryHelper;
  private final FilterFactory ff = CommonFactoryFinder.getFilterFactory(GeoTools.getDefaultHints());

  // we can safely use the tmp dir as a default here because we are running in a docker container so access is limited
  @Value("${tailormap-api.extract.location:#{systemProperties['java.io.tmpdir']}}")
  private String exportFilesLocation;

  @Value("${tailormap-api.extract.cleanup-minutes:120}")
  private int cleanupIntervalMinutes;

  public CreateLayerExtractService(
      SseEventBus eventBus, JsonMapper jsonMapper, FeatureSourceFactoryHelper featureSourceFactoryHelper) {
    this.eventBus = eventBus;
    this.featureSourceFactoryHelper = featureSourceFactoryHelper;
    // force unindented/single line output for SSE messages, because we may have set
    // spring.jackson.serialization.indent_output=true for debugging/development/test
    if (jsonMapper.isEnabled(SerializationFeature.INDENT_OUTPUT)) {
      this.jsonMapper = jsonMapper
          .rebuild()
          .configure(SerializationFeature.INDENT_OUTPUT, false)
          .build();
    } else {
      this.jsonMapper = jsonMapper;
    }
  }

  public String getExportFilesLocation() {
    return exportFilesLocation;
  }

  private void emitError(@NonNull String clientId, String details) {
    eventBus.handleEvent(SseEvent.builder()
        .addClientId(clientId)
        .data(jsonMapper.writeValueAsString(new ServerSentEventResponse()
            .eventType(ServerSentEventResponse.EventTypeEnum.EXTRACT_FAILED)
            .id(UUIDv7.randomV7())
            .details(Map.of(
                "message", "An error occurred during extract creation", "explanation", details))))
        .build());
  }

  public void emitProgress(
      @NonNull String clientId,
      @Nullable String fileId,
      int progress,
      boolean completed,
      @Nullable String message) {
    logger.debug("Emitting progress {} for layer with id {}", progress, clientId);

    message = StringUtils.isBlank(message) ? "Extract task started" : message;
    fileId = StringUtils.isBlank(fileId) ? "" : fileId;

    eventBus.handleEvent(SseEvent.builder()
        .addClientId(clientId)
        .data(jsonMapper.writeValueAsString(new ServerSentEventResponse()
            .eventType(
                completed
                    ? ServerSentEventResponse.EventTypeEnum.EXTRACT_COMPLETED
                    : ServerSentEventResponse.EventTypeEnum.EXTRACT_PROGRESS)
            .id(UUIDv7.randomV7())
            .details(Map.of(
                "progress",
                progress,
                "message",
                completed ? "Extract task completed" : message,
                "downloadId",
                fileId))))
        .build());
  }

  /**
   * Check the sse client id is valid and exists.
   *
   * @param clientId the SSE client id
   * @throws IllegalArgumentException when the SSE client id is invalid or not found on the event bus
   */
  public void validateClientId(@NonNull String clientId) throws IllegalArgumentException {
    if (!clientId.matches("[A-Za-z0-9_-]+")) {
      logger.warn("Invalid clientId for SSE connection: {}", clientId);
      throw new IllegalArgumentException("Invalid clientId");
    }

    // validate the given clientId is known on the event bus
    this.eventBus.getAllClientIds().stream()
        .filter(id -> Objects.equals(id, clientId))
        .findFirst()
        .ifPresentOrElse(id -> logger.debug("Validated clientId {}", id), () -> {
          throw new IllegalArgumentException("No active subscription found for clientId " + clientId);
        });
  }

  /**
   * Create a validated filename for an extract. The naming follows the pattern
   * {@code "%s_%s_%s.%s".formatted(sourceFT.getName(), clientId, UUIDv7.randomV7(), outputFormat.getExtension()) }
   * where the first part is the source feature type name (this is cleaned from some characters), the second part is
   * the SSE client id, the third part is a random UUIDv7 and the fourth part is the file extension based on the
   * requested output format.
   *
   * @param clientId the SSE client id
   * @param sourceFT the source featuretype for the extract
   * @param outputFormat the required format of the extract
   * @return the filename used to create an extract
   * @throws IllegalArgumentException when the SSE clientId is invalid or not found on the event bus
   */
  public String createExtractFilename(
      @NonNull String clientId,
      @NonNull TMFeatureType sourceFT,
      LayerExtractController.@NonNull ExtractOutputFormat outputFormat)
      throws IllegalArgumentException {

    this.validateClientId(clientId);

    String cleanFTName = sourceFT.getName();
    if (cleanFTName.contains(":")) {
      // clip off the WFS namespace part
      cleanFTName = cleanFTName.substring(cleanFTName.lastIndexOf(":") + 1);
      // remove: . _ which are used as separators in the filename and could cause issues when parsing the filename
      // later on
      cleanFTName = cleanFTName.replaceAll("[._]", "");
    }
    return "%s_%s_%s.%s".formatted(cleanFTName, clientId, UUIDv7.randomV7(), outputFormat.getExtension());
  }

  @Async("extractTaskExecutor")
  @Transactional
  public void createLayerExtract(
      @NonNull String clientId,
      @NonNull TMFeatureType inputTmFeatureType,
      @NonNull Set<String> attributes,
      String filterCQL,
      String sortBy,
      SortOrder sortOrder,
      LayerExtractController.@NonNull ExtractOutputFormat extractOutputFormat,
      @NonNull String outputFileName) {
    SimpleFeatureSource inputFeatureSource = null;

    this.emitProgress(clientId, outputFileName, 0, false, "Starting extract");

    try (Transaction outputTransaction = new DefaultTransaction("tailormap-extract-output")) {
      inputFeatureSource = featureSourceFactoryHelper.openGeoToolsFeatureSource(inputTmFeatureType);

      Query q = new Query(inputFeatureSource.getName().toString());
      if (!attributes.isEmpty()) {
        q.setPropertyNames(attributes.toArray(new String[0]));
      }

      if (!StringUtils.isBlank(filterCQL)) {
        Filter filter = ECQL.toFilter(filterCQL);
        q.setFilter(filter);
      }
      if (!StringUtils.isBlank(sortBy)) {
        q.setSortBy(ff.sort(sortBy, Objects.requireNonNullElse(sortOrder, SortOrder.ASCENDING)));
      }

      final int featCount = inputFeatureSource.getCount(q);
      logger.debug("Filtered source counts {}", featCount);
      final AtomicInteger featsAdded = new AtomicInteger();

      FileDataStore outputDataStore =
          getExtractDataStore(extractOutputFormat, outputFileName, clientId, inputTmFeatureType.getName());
      SimpleFeatureType fType =
          DataUtilities.createSubType(inputFeatureSource.getSchema(), attributes.toArray(new String[0]));
      outputDataStore.createSchema(fType);

      if (outputDataStore.getFeatureSource() instanceof SimpleFeatureStore featureStore) {
        featureStore.setTransaction(outputTransaction);
        featureStore.addFeatureListener(event -> {
          if (event.getType().equals(FeatureEvent.Type.ADDED)) {
            featsAdded.getAndIncrement();
          }
          if (featCount > 0) {
            if (featsAdded.get() % 50 == 0) {
              this.emitProgress(
                  clientId,
                  outputFileName,
                  (int) ((featsAdded.doubleValue() / featCount) * 100),
                  false,
                  null);
            }
          }
        });
        featureStore.addFeatures(inputFeatureSource.getFeatures(q));
        outputTransaction.commit();
      } else {
        this.emitError(clientId, "Output datastore is not a SimpleFeatureStore, cannot write features");
        logger.error("Output datastore is not a SimpleFeatureStore, cannot write features");
      }
      outputDataStore.dispose();
      this.emitProgress(clientId, outputFileName, 100, true, "Extract completed successfully");
    } catch (IOException | CQLException | SchemaException e) {
      emitError(clientId, e.getMessage());
      logger.error("Creating extract failed", e);
    } finally {
      if (inputFeatureSource != null) {
        try {
          inputFeatureSource.getDataStore().dispose();
        } catch (Exception e) {
          logger.warn("Error disposing datastore for feature source {}", inputFeatureSource.getName(), e);
        }
      }
    }
  }

  private FileDataStore getExtractDataStore(
      LayerExtractController.ExtractOutputFormat extractOutputFormat,
      String outputFileName,
      String clientId,
      String typeName)
      throws IOException {

    final File outputFile = Files.createFile(Path.of(exportFilesLocation, outputFileName))
        .toFile()
        .getCanonicalFile();
    if (!outputFile
        .getPath()
        .startsWith(Path.of(exportFilesLocation).toFile().getCanonicalPath())) {
      throw new IOException("Invalid file path");
    }

    if (!logger.isDebugEnabled()) {
      // delete in production after JVM exit because the event bus will be reset when the JVM exits, and then we
      // are unlikely to have a reference to the file anymore.
      // In debug/development mode we want to keep the file for inspection.
      outputFile.deleteOnExit();
    }

    switch (extractOutputFormat) {
      case CSV -> {
        Map<String, Serializable> params = Map.of(
            CSVDataStoreFactory.FILE_PARAM.key,
            outputFile,
            CSVDataStoreFactory.STRATEGYP.key,
            CSVDataStoreFactory.WKT_STRATEGY,
            CSVDataStoreFactory.WKTP.key,
            "the_geom_wkt",
            CSVDataStoreFactory.WRITEPRJ.key,
            false,
            CSVDataStoreFactory.QUOTEALL.key,
            true);
        return (FileDataStore) new CSVDataStoreFactory().createNewDataStore(params);
      }
      case XLSX -> {
        Map<String, Serializable> params = Map.of(
            ExcelDataStoreFactory.FILE_PARAM.key,
            outputFile,
            ExcelDataStoreFactory.SHEET_PARAM.key,
            // typeName could hve a prefix; for Excel sheet names ':' is disallowed, max length is 31
            typeName.substring(typeName.lastIndexOf(":") + 1, Math.min(typeName.length(), 31)));
        return (FileDataStore) new ExcelDataStoreFactory().createNewDataStore(params);
      }
      // TODO implement
      case GEOJSON, SHAPE -> {
        emitError(clientId, "Output format " + extractOutputFormat + " is not yet supported");
        logger.error("Output format {} is not yet supported", extractOutputFormat);
        throw new IOException("Unsupported output format: " + extractOutputFormat);
      }
      default -> {
        // should never happen
        emitError(clientId, "Unknown output format: " + extractOutputFormat);
        logger.error("Unknown output format: {}", extractOutputFormat);
        throw new IllegalArgumentException("Unknown output format: " + extractOutputFormat);
      }
    }
  }

  /**
   * Cleanup expired extract files. Filenames are created in {@link CreateLayerExtractService#createExtractFilename }
   * and follow the pattern {@code "%s_%s_%s.%s".formatted(sourceFT.getName(), clientId, UUIDv7.randomV7(),
   * outputFormat.getExtension()) }
   */
  @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES, initialDelay = 15)
  public void cleanupExpiredExtracts() {
    logger.debug("Running expired extracts cleanup...");
    List<FileWithAttributes> clientFilesOnDisk = new ArrayList<>();
    Set<String> validClientIds = eventBus.getAllClientIds();

    // list download files in export location and delete those that are not bound to an active sse stream client
    try (Stream<Path> stream = Files.walk(Path.of(exportFilesLocation))) {
      stream.filter(Files::isRegularFile).forEach(path -> {
        File file = path.toFile();
        String filename = file.getName();
        String[] parts = filename.split("[_.]", -1);
        if (parts.length < 4) {
          logger.warn("Unexpected file in extract location: {}", filename);
          return;
        }
        String clientId = parts[1];
        if (!validClientIds.contains(clientId)) {
          if (!file.delete()) {
            logger.error("Failed to delete unattached extract file {}", filename);
          }
        } else {
          Instant timestampPart = UUIDv7.timestampAsInstant(UUIDv7.fromString(parts[2]));
          clientFilesOnDisk.add(new FileWithAttributes(file, timestampPart, clientId));
        }
      });

      // delete any files are older than the cutoff
      clientFilesOnDisk.stream()
          .filter(f -> f.timestamp()
              .isBefore(Instant.now().minusSeconds(TimeUnit.MINUTES.toSeconds(cleanupIntervalMinutes))))
          .forEach(f -> {
            if (!f.file().delete()) {
              logger.error(
                  "Failed to delete expired extract file {}",
                  f.file().getName());
            }
          });
    } catch (IOException e) {
      logger.error("Error while cleaning up expired extracts", e);
    }
  }

  private record FileWithAttributes(File file, Instant timestamp, String clientId) {}
}
