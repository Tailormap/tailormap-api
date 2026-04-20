/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static org.tailormap.api.persistence.helper.TMFeatureTypeHelper.getConfiguredAttributes;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import jakarta.validation.Valid;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.geotools.api.data.Query;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.filter.Filter;
import org.geotools.api.filter.sort.SortOrder;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.filter.text.ecql.ECQL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.tailormap.api.annotation.AppRestController;
import org.tailormap.api.geotools.data.excel.ExcelDataStore;
import org.tailormap.api.geotools.featuresources.FeatureSourceFactoryHelper;
import org.tailormap.api.persistence.Application;
import org.tailormap.api.persistence.GeoService;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.persistence.json.AppLayerSettings;
import org.tailormap.api.persistence.json.AppTreeLayerNode;
import org.tailormap.api.persistence.json.GeoServiceLayer;
import org.tailormap.api.repository.FeatureSourceRepository;
import org.tailormap.api.service.CreateLayerExtractService;

@AppRestController
@RequestMapping(path = "${tailormap-api.base-path}/{viewerKind}/{viewerName}/layer/{appLayerId}/extract")
public class LayerExtractController {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final Pattern SAFE_DOWNLOAD_ID = Pattern.compile("^[A-Za-z0-9._-]+$");
  private final FeatureSourceRepository featureSourceRepository;
  private final CreateLayerExtractService createLayerExtractService;
  private final FeatureSourceFactoryHelper featureSourceFactoryHelper;

  @Value("#{'${tailormap-api.extract.allowed-outputformats}'.split(',')}")
  private List<ExtractOutputFormat> allowedExtractOutputFormats;

  public LayerExtractController(
      FeatureSourceRepository featureSourceRepository,
      CreateLayerExtractService createLayerExtractService,
      FeatureSourceFactoryHelper featureSourceFactoryHelper) {
    this.featureSourceRepository = featureSourceRepository;
    this.createLayerExtractService = createLayerExtractService;
    this.featureSourceFactoryHelper = featureSourceFactoryHelper;
  }

  /**
   * Download the result of an extract request. The extract generation should be initiated first by a POST to
   * {@code /{viewerKind}/{viewerName}/layer/{appLayerId}/extract/{clientId}}.
   */
  @GetMapping(path = "/download/{downloadId}")
  @Counted(value = "tailormap_api_extract_download", description = "Count of layer extract downloads")
  public ResponseEntity<?> download(
      @ModelAttribute GeoService service,
      @ModelAttribute GeoServiceLayer layer,
      @ModelAttribute Application application,
      @ModelAttribute AppTreeLayerNode appTreeLayerNode,
      @PathVariable String downloadId)
      throws MalformedURLException {

    if (downloadId == null || !SAFE_DOWNLOAD_ID.matcher(downloadId).matches()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid downloadId");
    }
    Path exportRoot = Path.of(createLayerExtractService.getExportFilesLocation())
        .toAbsolutePath()
        .normalize();
    Path filePath = exportRoot.resolve(downloadId).normalize();
    if (!filePath.startsWith(exportRoot)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid downloadId");
    }

    Resource resource = new UrlResource(filePath.toUri());
    if (!resource.exists() || !resource.isReadable() || !resource.isFile()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Download file not found");
    }

    String contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
    try {
      String detectedContentType = Files.probeContentType(filePath);
      if (detectedContentType != null) {
        contentType = detectedContentType;
      }
    } catch (IOException e) {
      logger.debug("Could not determine content type for {}", filePath, e);
    }

    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(contentType))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filePath.getFileName() + "\"")
        .body(resource);
  }

  @GetMapping("/formats")
  public ResponseEntity<?> formats(
      @Valid @ModelAttribute GeoServiceLayer layer,
      @ModelAttribute GeoService service,
      @ModelAttribute Application application,
      @ModelAttribute AppTreeLayerNode appTreeLayerNode) {
    return ResponseEntity.ok(allowedExtractOutputFormats.stream()
        .map(ExtractOutputFormat::getValue)
        .toList());
  }

  @Transactional
  @PostMapping("/{clientId}")
  @Timed(value = "tailormap_api_extract", description = "Time taken to process a layer extract request")
  public ResponseEntity<?> extract(
      @Valid @ModelAttribute GeoServiceLayer layer,
      @ModelAttribute GeoService service,
      @ModelAttribute Application application,
      @ModelAttribute AppTreeLayerNode appTreeLayerNode,
      @PathVariable String clientId,
      @RequestParam ExtractOutputFormat outputFormat,
      @RequestParam(required = false) Set<String> attributes,
      @RequestParam(required = false) String filter,
      @RequestParam(required = false) String sortBy,
      @RequestParam(required = false, defaultValue = "asc") String sortOrder) {

    try {
      createLayerExtractService.validateClientId(clientId);
    } catch (IllegalArgumentException e) {
      logger.warn("Invalid clientId for extract request: {}", clientId);
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    if (!allowedExtractOutputFormats.contains(outputFormat)) {
      logger.debug("Invalid output format requested: {}", outputFormat);
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid output format");
    }

    TMFeatureType sourceFT = service.findFeatureTypeForLayer(layer, featureSourceRepository);
    if (sourceFT == null) {
      logger.debug("Layer export requested for layer without feature type");
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    if (attributes == null) {
      attributes = new HashSet<>();
    }

    AppLayerSettings appLayerSettings = application.getAppLayerSettings(appTreeLayerNode);
    // Get attributes in configured or original order
    Set<String> nonHiddenAttributes =
        getConfiguredAttributes(sourceFT, appLayerSettings).keySet();

    if (!attributes.isEmpty()) {
      // Only export non-hidden property names
      if (!nonHiddenAttributes.containsAll(attributes)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "One or more requested attributes are not available on the feature type");
      }
    } else if (!sourceFT.getSettings().getHideAttributes().isEmpty()) {
      // Only specify specific propNames if there are hidden attributes. Having no propNames
      // request parameter to request all propNames is less error-prone than specifying the ones
      // we have saved in the feature type
      attributes = new HashSet<>(nonHiddenAttributes);
    }

    // Empty attributes means we won't specify propNames in the GetFeature request. However, if we do select only
    // some property names, we need the geometry attribute which is not in the 'attributes' request param so spatial
    // export formats don't have the geometry missing.
    if (!attributes.isEmpty() && sourceFT.getDefaultGeometryAttribute() != null) {
      attributes.add(sourceFT.getDefaultGeometryAttribute());
    }

    if (outputFormat == ExtractOutputFormat.XLSX) {
      validateExcelLimits(sourceFT, attributes, filter);
    }

    SortOrder sortingOrder = SortOrder.ASCENDING;
    if (null != sortOrder && (sortOrder.equalsIgnoreCase("desc") || sortOrder.equalsIgnoreCase("asc"))) {
      sortingOrder = SortOrder.valueOf(sortOrder.toUpperCase(Locale.ROOT));
    }

    final String outputFileName =
        this.createLayerExtractService.createExtractFilename(clientId, sourceFT, outputFormat);
    this.createLayerExtractService.emitProgress(clientId, outputFileName, 0, false, "Extract task received");

    //noinspection JvmTaintAnalysis Not a Path Traversal Sink because the clientId is validated
    this.createLayerExtractService.createLayerExtract(
        clientId, sourceFT, attributes, filter, sortBy, sortingOrder, outputFormat, outputFileName);

    //noinspection JvmTaintAnalysis Not an XSS sink because the response is a json message
    return ResponseEntity.accepted()
        .body(Map.of("message", "Extract request accepted", "downloadId", outputFileName));
  }

  /**
   * Check that neither the number of columns nor the number of rows requested for the extract exceed the limits of
   * Excel format. This is required to block extract requests that would fail later on in the ExcelFeatureWriter when
   * the limits are exceeded. NOTE: cell size limits are handled in the ExcelFeatureWriter.
   *
   * @param featureType requested FT
   * @param attributes requested attributes
   * @param filterCQL requested filter
   */
  private void validateExcelLimits(TMFeatureType featureType, Set<String> attributes, String filterCQL) {
    if (attributes.size() > ExcelDataStore.getMaxColumns()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Excel format does not support more than " + ExcelDataStore.getMaxColumns() + " columns");
    }
    SimpleFeatureSource inputFeatureSource = null;
    try {
      // count all the features; this is expensive but required to block extract when the Excel limits for
      // row/columns are exceeded
      inputFeatureSource = featureSourceFactoryHelper.openGeoToolsFeatureSource(featureType);
      Query q = new Query(inputFeatureSource.getName().toString());
      if (!attributes.isEmpty()) {
        q.setPropertyNames(attributes.toArray(new String[0]));
      }

      if (!StringUtils.isBlank(filterCQL)) {
        Filter filter = ECQL.toFilter(filterCQL);
        q.setFilter(filter);
      }
      final int featCount = inputFeatureSource.getCount(q);
      if (featCount >= ExcelDataStore.getMaxRows()) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Excel format does not support more than " + ExcelDataStore.getMaxRows() + " rows");
      }
    } catch (CQLException | IOException e) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "Failed to count all features for Excel extract: " + e.getMessage());
    } finally {
      if (inputFeatureSource != null) {
        inputFeatureSource.getDataStore().dispose();
      }
    }
  }

  public enum ExtractOutputFormat {
    CSV("csv", "csv"),
    GEOJSON("geojson", "json"),
    XLSX("xlsx", "xlsx"),
    SHAPE("shape", "zip");

    private final String value;
    private final String extension;

    ExtractOutputFormat(String value, String extension) {
      this.value = value;
      this.extension = extension;
    }

    public static ExtractOutputFormat fromValue(String value) {
      for (ExtractOutputFormat format : ExtractOutputFormat.values()) {
        if (format.value.equalsIgnoreCase(value)) {
          return format;
        }
      }
      throw new IllegalArgumentException("Invalid output format: " + value);
    }

    public String getValue() {
      return this.value;
    }

    public String getExtension() {
      return this.extension;
    }

    @Override
    public String toString() {
      return String.valueOf(this.value);
    }
  }
}
