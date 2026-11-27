/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.rasc.sse.eventbus.SseEvent;
import ch.rasc.sse.eventbus.SseEventBus;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.tailormap.api.controller.LayerExtractController.ExtractOutputFormat;
import org.tailormap.api.geotools.featuresources.FeatureSourceFactoryHelper;
import org.tailormap.api.persistence.TMFeatureType;
import tools.jackson.databind.json.JsonMapper;

class CreateLayerExtractServiceTest {
  @TempDir
  private Path tempDir;

  /**
   * The extract is created asynchronously, so any exception must be reported to the client with an SSE event,
   * otherwise the client waits indefinitely for the extract.
   */
  @ParameterizedTest
  @EnumSource(ExtractOutputFormat.class)
  @SuppressWarnings("PMD.DisposeDataStore") // openGeoToolsFeatureSource() is only stubbed, nothing to dispose
  void should_emit_error_for_any_exception(ExtractOutputFormat outputFormat) throws Exception {
    SseEventBus eventBus = mock(SseEventBus.class);
    FeatureSourceFactoryHelper featureSourceFactoryHelper = mock(FeatureSourceFactoryHelper.class);
    when(featureSourceFactoryHelper.openGeoToolsFeatureSource(any(TMFeatureType.class)))
        .thenThrow(new IllegalStateException("Something unexpected went wrong"));

    CreateLayerExtractService service = new CreateLayerExtractService(
        eventBus, JsonMapper.builder().build(), featureSourceFactoryHelper, mock(ZipService.class));
    ReflectionTestUtils.setField(service, "exportFilesBaseLocation", tempDir.toString());
    ReflectionTestUtils.setField(service, "progressReportInterval", 100);
    service.initializeExtractDirectory();

    service.createLayerExtract(
        "test-client",
        new TMFeatureType().setName("test_feature_type"),
        Set.of("naam"),
        null,
        null,
        null,
        outputFormat,
        "test-extract" + outputFormat.getExtension());

    ArgumentCaptor<SseEvent> events = ArgumentCaptor.forClass(SseEvent.class);
    verify(eventBus, atLeast(2)).handleEvent(events.capture());
    List<SseEvent> allEvents = events.getAllValues();
    String lastEventData = String.valueOf(allEvents.getLast().data());
    assertThat(lastEventData, containsString("extract-failed"));
    assertThat(lastEventData, containsString("Something unexpected went wrong"));
  }
}
