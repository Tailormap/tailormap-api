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
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
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
import org.geotools.data.geojson.store.GeoJSONDataStoreFactory;
import org.geotools.data.shapefile.ShapefileDumper;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.SchemaException;
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
import org.springframework.web.server.ResponseStatusException;
import org.tailormap.api.controller.LayerExtractController;
import org.tailormap.api.geotools.collection.ProgressReportingFeatureCollection;
import org.tailormap.api.geotools.data.excel.ExcelDataStore;
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

  // we can safely use the tmp dir as a default here because we are running in a docker container without a shell so
  // access is limited
  @Value("${tailormap-api.extract.location:#{systemProperties['java.io.tmpdir']}}")
  private String exportFilesLocation;

  @Value("${tailormap-api.extract.cleanup-minutes:120}")
  private int cleanupIntervalMinutes;

  @Value("#{T(java.lang.Math).max(1, ${tailormap-api.extract.progress-report-interval:100})}")
  private int progressReportInterval;

  @Value("${tailormap-api.features.wfs_count_exact:false}")
  private boolean exactWfsCounts;

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
    message = StringUtils.isBlank(message) ? "Extract task started" : message;
    fileId = StringUtils.isBlank(fileId) ? "" : fileId;
    logger.debug("Emitting progress {}% for client [{}], message: '{}'", progress, clientId, message);

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
   * Check that the sse client id is valid and exists.
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
   * {@code "%s_%s_%s%s".formatted(sourceFT.getName(), clientId, UUIDv7.randomV7(), outputFormat.getExtension()) }
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
      // remove: '.' and '_' which are used as separators in the filename and could cause issues when parsing the
      // filename later on
      cleanFTName = cleanFTName.replaceAll("[._]", "");
    }
    return "%s_%s_%s%s".formatted(cleanFTName, clientId, UUIDv7.randomV7(), outputFormat.getExtension());
  }

  @Async("extractTaskExecutor")
  @Transactional
  public void createLayerExtract(
      @NonNull String clientId,
      @NonNull TMFeatureType inputTmFeatureType,
      @NonNull Set<String> attributes,
      @Nullable Filter filter,
      String sortBy,
      SortOrder sortOrder,
      LayerExtractController.@NonNull ExtractOutputFormat extractOutputFormat,
      @NonNull String outputFileName) {

    this.emitProgress(clientId, outputFileName, 0, false, "Starting extract");

    switch (extractOutputFormat) {
      case SHAPE ->
        this.handleWithShapeDumper(
            clientId, inputTmFeatureType, attributes, filter, sortBy, sortOrder, outputFileName);
      case CSV, GEOJSON, XLSX ->
        this.handleSingleFileFormats(
            clientId,
            inputTmFeatureType,
            attributes,
            filter,
            sortBy,
            sortOrder,
            extractOutputFormat,
            outputFileName);
    }
  }

  private void handleSingleFileFormats(
      @NonNull String clientId,
      @NonNull TMFeatureType inputTmFeatureType,
      @NonNull Set<String> attributes,
      Filter filter,
      String sortBy,
      SortOrder sortOrder,
      LayerExtractController.@NonNull ExtractOutputFormat extractOutputFormat,
      @NonNull String outputFileName) {

    SimpleFeatureSource inputFeatureSource = null;
    FileDataStore outputDataStore = null;
    try (Transaction outputTransaction = new DefaultTransaction("tailormap-extract-output")) {
      inputFeatureSource = featureSourceFactoryHelper.openGeoToolsFeatureSource(inputTmFeatureType);

      Query q = createQuery(inputFeatureSource, attributes, filter, sortBy, sortOrder);

      int featCount = getFeatureCount(inputFeatureSource, q);

      if (extractOutputFormat == LayerExtractController.ExtractOutputFormat.XLSX
          && featCount >= ExcelDataStore.getMaxRows()) {
        this.emitError(
            clientId,
            "Extract result contains %d features, which exceeds the maximum of %d for Excel output format. Please refine your filter or choose a different output format."
                .formatted(featCount, ExcelDataStore.getMaxRows()));
        throw new ResponseStatusException(
            org.springframework.http.HttpStatus.BAD_REQUEST,
            "Extract result contains %d features, which exceeds the maximum of %d for Excel output format. Please refine your filter or choose a different output format."
                .formatted(featCount, ExcelDataStore.getMaxRows()));
      }

      outputDataStore = this.getExtractDataStore(
          extractOutputFormat, outputFileName, clientId, inputTmFeatureType.getName());
      SimpleFeatureType fType =
          DataUtilities.createSubType(inputFeatureSource.getSchema(), attributes.toArray(new String[0]));
      outputDataStore.createSchema(fType);

      if (outputDataStore instanceof ExcelDataStore excelDataStore) {
        excelDataStore.setEnableCellAutoSizing(featCount >= 0 && featCount < 1000);
      }

      final AtomicInteger featsAdded = new AtomicInteger();
      if (outputDataStore.getFeatureSource() instanceof SimpleFeatureStore featureStore) {
        featureStore.setTransaction(outputTransaction);
        featureStore.addFeatureListener(event -> {
          if (event.getType().equals(FeatureEvent.Type.ADDED)) {
            featsAdded.getAndIncrement();
          }
          if (featCount > 0) {
            if (featsAdded.get() % progressReportInterval == 0) {
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
        outputDataStore.dispose();
        this.emitProgress(clientId, outputFileName, 100, true, "Extract completed successfully");
      } else {
        outputDataStore.dispose();
        this.emitError(clientId, "Output datastore is not a SimpleFeatureStore, cannot write features");
        logger.error("Output datastore is not a SimpleFeatureStore, cannot write features");
      }
    } catch (IOException | SchemaException | IllegalArgumentException e) {
      emitError(clientId, e.getMessage());
      logger.error("Creating extract failed", e);
    } finally {
      if (outputDataStore != null) {
        outputDataStore.dispose();
      }
      if (inputFeatureSource != null) {
        try {
          inputFeatureSource.getDataStore().dispose();
        } catch (Exception e) {
          logger.warn("Error disposing datastore for feature source {}", inputFeatureSource.getName(), e);
        }
      }
    }
  }

  private File getValidatedOutputFile(String outputFileName) throws IOException {
    Path exportRoot = Path.of(exportFilesLocation).toRealPath();
    Path outputPath = exportRoot.resolve(outputFileName).normalize();
    if (!outputPath.startsWith(exportRoot)) {
      throw new IOException("Invalid file path");
    }
    Path createdFilePath = Files.createFile(outputPath).toRealPath();
    if (!createdFilePath.startsWith(exportRoot)) {
      throw new IOException("Invalid file path");
    }
    return createdFilePath.toFile();
  }

  /**
   * Create a writable GeoTools {@link FileDataStore} for the requested extract format. The format must be must be
   * supported by a {@link FileDataStore} implementation, for example CSV, Excel or GeoJSON. For unsupported formats
   * (for example Shapefile) a custom handling is used in the calling method.
   *
   * <p>The output file is validated to ensure it is created under the configured extract location.
   *
   * @param extractOutputFormat the requested extract output format
   * @param outputFileName the target output filename
   * @param clientId the SSE client id, used for error reporting
   * @param typeName the source feature type name, used to derive format-specific metadata (for example Excel sheet
   *     name)
   * @return a newly created {@link FileDataStore} configured for the requested format
   * @throws IOException when the output file path is invalid or the datastore cannot be created
   */
  private FileDataStore getExtractDataStore(
      LayerExtractController.ExtractOutputFormat extractOutputFormat,
      String outputFileName,
      String clientId,
      String typeName)
      throws IOException {

    final File outputFile = getValidatedOutputFile(outputFileName);
    if (!logger.isDebugEnabled()) {
      // delete in production after JVM exit because the event bus will be reset when the JVM exits, and then we
      // are unlikely to have a reference to the file anymore.
      // In debug/development mode we want to keep the file for inspection.
      outputFile.deleteOnExit();
    }

    switch (extractOutputFormat) {
      case CSV -> {
        return (FileDataStore) new CSVDataStoreFactory()
            .createNewDataStore(Map.of(
                CSVDataStoreFactory.FILE_PARAM.key,
                outputFile,
                CSVDataStoreFactory.STRATEGYP.key,
                CSVDataStoreFactory.WKT_STRATEGY,
                CSVDataStoreFactory.WKTP.key,
                "the_geom_wkt",
                CSVDataStoreFactory.WRITEPRJ.key,
                false,
                CSVDataStoreFactory.QUOTEALL.key,
                true));
      }
      case XLSX -> {
        // replace any invalid characters such as /\?*[] with '_' and clip to 31 characters because Excel has
        // limitations on sheet names. Also clip off any WFS namespace prefix in the type name, which is often
        // separated by a ':' character, because ':' is not allowed in Excel sheet names.
        typeName = typeName.contains(":")
            ? typeName.substring(typeName.lastIndexOf(":") + 1).replaceAll("[\\\\/?*\\[\\]:]", "_")
            : typeName.replaceAll("[\\\\/?*\\[\\]:]", "_");
        typeName = typeName.substring(0, Math.min(typeName.length(), 31));
        return (FileDataStore) new ExcelDataStoreFactory()
            .createNewDataStore(Map.of(
                ExcelDataStoreFactory.FILE_PARAM.key,
                outputFile,
                ExcelDataStoreFactory.SHEET_PARAM.key,
                typeName));
      }
      case GEOJSON -> {
        return (FileDataStore) new GeoJSONDataStoreFactory()
            .createNewDataStore(Map.of(GeoJSONDataStoreFactory.FILE_PARAM.key, outputFile));
      }
      default -> {
        // should never happen
        emitError(clientId, "Unknown output format: " + extractOutputFormat);
        logger.error("Unknown output format: {}", extractOutputFormat);
        throw new IllegalArgumentException("Unknown output format: " + extractOutputFormat);
      }
    }
  }

  private int getFeatureCount(SimpleFeatureSource source, Query query) throws IOException {
    int count = source.getCount(query);
    logger.debug("Filtered source counts {} features", count);
    if (count < 0 && exactWfsCounts) {
      count = source.getFeatures(query).size();
    }
    return count;
  }

  private void handleWithShapeDumper(
      @NonNull String clientId,
      @NonNull TMFeatureType inputTmFeatureType,
      @NonNull Set<String> attributes,
      Filter filter,
      String sortBy,
      SortOrder sortOrder,
      @NonNull String outputFileName) {
    SimpleFeatureSource inputFeatureSource = null;
    File outputDirectory = null;
    try {
      File outputFile = getValidatedOutputFile(outputFileName);
      String baseName = outputFile
          .getName()
          .substring(
              0,
              outputFile
                  .getName()
                  .lastIndexOf(LayerExtractController.ExtractOutputFormat.SHAPE.getExtension()));
      outputDirectory = outputFile
          .getParentFile()
          .toPath()
          .resolve(baseName)
          .toFile()
          .getCanonicalFile();
      if (logger.isDebugEnabled()) {
        // delete in production after JVM exit because the event bus will be reset when the JVM exits, and then
        // we
        // are unlikely to have a reference to the file anymore.
        // In debug/development mode we want to keep the directory for inspection.
        outputDirectory.deleteOnExit();
      }
      Files.createDirectories(outputDirectory.toPath());

      ShapefileDumper dumper = new ShapefileDumper(outputDirectory);
      dumper.setCharset(StandardCharsets.UTF_8);
      dumper.setEmptyShapefileAllowed(false);

      inputFeatureSource = featureSourceFactoryHelper.openGeoToolsFeatureSource(inputTmFeatureType);

      Query q = createQuery(inputFeatureSource, attributes, filter, sortBy, sortOrder);

      final int featCount = getFeatureCount(inputFeatureSource, q);
      final boolean hasKnownFeatureCount = featCount > 0;

      AtomicInteger lastProgress = new AtomicInteger(0);

      dumper.dump(new ProgressReportingFeatureCollection(
          inputFeatureSource.getFeatures(q), progressReportInterval, processed -> {
            int progress = hasKnownFeatureCount ? (int) ((processed / (double) featCount) * 99) : 0;
            lastProgress.set(progress);
            String progressMessage = hasKnownFeatureCount
                ? "Extracting shapes: %d/%d features processed".formatted(processed, featCount)
                : "Extracting shapes: %d features processed".formatted(processed);
            this.emitProgress(clientId, outputFileName, progress, false, progressMessage);
          }));
      this.emitProgress(
          clientId,
          outputFileName,
          Math.max(99, lastProgress.get()),
          false,
          "Extract shapes dumped successfully");

      zipDirectory(outputDirectory.toPath(), outputFile.toPath());
      this.emitProgress(clientId, outputFileName, 100, true, "Extract completed successfully");
    } catch (IOException | IllegalArgumentException e) {
      emitError(clientId, e.getMessage());
      logger.error("Creating extract failed", e);
    } finally {
      if (outputDirectory != null) {
        try {
          deleteDirectoryRecursively(outputDirectory.toPath());
        } catch (IOException e) {
          logger.error("Failed to delete output directory {}", outputDirectory, e);
        }
      }
      if (inputFeatureSource != null) {
        try {
          inputFeatureSource.getDataStore().dispose();
        } catch (Exception e) {
          logger.warn("Error disposing datastore for feature source {}", inputFeatureSource.getName(), e);
        }
      }
    }
  }

  private Query createQuery(
      SimpleFeatureSource inputFeatureSource,
      Set<String> attributes,
      Filter filter,
      String sortBy,
      SortOrder sortOrder) {
    Query q = new Query(inputFeatureSource.getName().toString());
    if (!attributes.isEmpty()) {
      q.setPropertyNames(attributes.toArray(new String[0]));
    }

    if (filter != null) {
      q.setFilter(filter);
    }
    if (!StringUtils.isBlank(sortBy)) {
      q.setSortBy(ff.sort(sortBy, Objects.requireNonNullElse(sortOrder, SortOrder.ASCENDING)));
    }
    return q;
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

      try (Stream<Path> paths = Files.walk(Path.of(exportFilesLocation))) {
        paths.filter(Files::isDirectory).forEach(path -> {
          File file = path.toFile();
          String filename = file.getName();
          String[] parts = filename.split("[_]", -1);
          if (parts.length < 3) {
            logger.warn("Unexpected directory in extract location: {}", filename);
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
      }

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

  private void zipDirectory(Path sourceDir, Path zipFile) throws IOException {
    try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile));
        Stream<Path> pathStream = Files.walk(sourceDir)) {
      pathStream.filter(Files::isRegularFile).forEach(path -> {
        String entryName = sourceDir.relativize(path).toString().replace(File.separatorChar, '/');
        try {
          zos.putNextEntry(new ZipEntry(entryName));
          Files.copy(path, zos);
          zos.closeEntry();
        } catch (IOException e) {
          throw new RuntimeException("Failed to add file to zip: " + path, e);
        }
      });
    } catch (RuntimeException e) {
      if (e.getCause() instanceof IOException ioException) {
        throw ioException;
      }
      throw e;
    }
  }

  private void deleteDirectoryRecursively(Path directory) throws IOException {
    try (Stream<Path> paths = Files.walk(directory)) {
      paths.sorted(Comparator.reverseOrder()).forEach(path -> {
        try {
          logger.debug("Deleting path {}", path);
          Files.deleteIfExists(path);
        } catch (IOException e) {
          throw new RuntimeException("Failed to delete path: " + path, e);
        }
      });
    } catch (RuntimeException e) {
      if (e.getCause() instanceof IOException ioException) {
        throw ioException;
      }
      throw e;
    }
  }

  private record FileWithAttributes(File file, Instant timestamp, String clientId) {}
}
