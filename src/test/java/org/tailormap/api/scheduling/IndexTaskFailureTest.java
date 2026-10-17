package org.tailormap.api.scheduling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.rasc.sse.eventbus.SseEventBus;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.tailormap.api.geotools.featuresources.FeatureSourceFactoryHelper;
import org.tailormap.api.persistence.SearchIndex;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.repository.FeatureTypeRepository;
import org.tailormap.api.repository.SearchIndexRepository;
import org.tailormap.api.solr.SolrService;
import tools.jackson.databind.json.JsonMapper;

class IndexTaskFailureTest {

  @Test
  void failure_starting_solr_marks_index_error_and_invalidates_spatial_version() {
    SearchIndexRepository indexes = mock(SearchIndexRepository.class);
    FeatureTypeRepository featureTypes = mock(FeatureTypeRepository.class);
    FeatureSourceFactoryHelper sources = mock(FeatureSourceFactoryHelper.class);
    SolrService solr = mock(SolrService.class);

    SearchIndex index = new SearchIndex()
        .setId(7L)
        .setName("Test index")
        .setFeatureTypeId(99L)
        .setSpatialIndexVersion(2);

    when(indexes.findById(7L)).thenReturn(Optional.of(index));
    when(indexes.save(any(SearchIndex.class))).thenAnswer(invocation -> invocation.getArgument(0));

    when(featureTypes.findById(99L)).thenReturn(Optional.of(new TMFeatureType()));

    when(solr.getSolrClientForIndexing()).thenThrow(new IllegalStateException("Simulated Solr failure"));

    JobExecutionContext context = mock(JobExecutionContext.class);
    JobDetail job = mock(JobDetail.class);
    when(context.getJobDetail()).thenReturn(job);
    when(job.getJobDataMap()).thenReturn(new JobDataMap());

    IndexTask task =
        new IndexTask(indexes, featureTypes, sources, solr, mock(SseEventBus.class), mock(JsonMapper.class));
    task.setIndexId(7L);

    assertThrows(JobExecutionException.class, () -> task.executeInternal(context));

    ArgumentCaptor<SearchIndex> saved = ArgumentCaptor.forClass(SearchIndex.class);
    verify(indexes).save(saved.capture());

    assertEquals(SearchIndex.Status.ERROR, saved.getValue().getStatus());
    assertEquals(1, saved.getValue().getSpatialIndexVersion());
  }
}
