/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.geotools.api.data.DataStore;
import org.geotools.api.data.SimpleFeatureStore;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.filter.Filter;
import org.geotools.api.filter.FilterFactory;
import org.geotools.api.filter.identity.FeatureId;
import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.util.factory.Hints;
import org.tailormap.api.geotools.featuresources.JDBCFeatureSourceHelper;
import org.tailormap.api.persistence.TMFeatureType;

/**
 * Restores features in the spatial test databases changed by a test, so running the integration tests again against the
 * same test data containers gives the same results.
 *
 * <p>Call {@link #save(TMFeatureType, String...)} for the features a test modifies or deletes and
 * {@link #removeOnRestore(TMFeatureType, String...)} for the features a test inserts before the test runs, and
 * {@link #restore()} afterwards. Restoring deletes the inserted and saved features and inserts the saved features again
 * with their original feature ids. Note that deleting a feature also deletes its attachments (the attachments tables
 * have an {@code ON DELETE CASCADE} foreign key).
 */
public class SpatialTestDataRestorer {
  private static final FilterFactory ff = CommonFactoryFinder.getFilterFactory();

  private record FeatureTypeState(
      TMFeatureType featureType, List<SimpleFeature> saved, Set<String> removeOnRestore) {}

  private final Map<String, FeatureTypeState> states = new LinkedHashMap<>();

  private FeatureTypeState state(TMFeatureType featureType) {
    return states.computeIfAbsent(
        featureType.getFeatureSource().getId() + ":" + featureType.getName(),
        k -> new FeatureTypeState(featureType, new ArrayList<>(), new LinkedHashSet<>()));
  }

  private static Filter idFilter(Set<String> fids) {
    Set<FeatureId> ids = fids.stream().map(ff::featureId).collect(Collectors.toSet());
    return ff.id(ids);
  }

  private static Set<String> fids(String... fids) {
    return new LinkedHashSet<>(Arrays.asList(fids));
  }

  /**
   * Save the current state of features which a test modifies or deletes, to restore them later.
   *
   * @throws IllegalStateException if a feature does not exist
   */
  public void save(TMFeatureType featureType, String... fids) throws IOException {
    DataStore ds = new JDBCFeatureSourceHelper().createDataStore(featureType.getFeatureSource());
    try {
      List<SimpleFeature> features = DataUtilities.list(
          ds.getFeatureSource(featureType.getName()).getFeatures(idFilter(fids(fids))));
      if (features.size() != fids.length) {
        throw new IllegalStateException("Expected features %s of feature type %s to exist, found %d"
            .formatted(Arrays.toString(fids), featureType.getName(), features.size()));
      }
      state(featureType).saved().addAll(features);
    } finally {
      ds.dispose();
    }
  }

  /**
   * Delete features which a test inserts when restoring. The features are also deleted now, in case a previous test
   * run did not clean them up.
   */
  public void removeOnRestore(TMFeatureType featureType, String... fids) throws IOException {
    state(featureType).removeOnRestore().addAll(fids(fids));
    DataStore ds = new JDBCFeatureSourceHelper().createDataStore(featureType.getFeatureSource());
    try {
      ((SimpleFeatureStore) ds.getFeatureSource(featureType.getName())).removeFeatures(idFilter(fids(fids)));
    } finally {
      ds.dispose();
    }
  }

  /** Delete the inserted and saved features and insert the saved features again. */
  public void restore() throws IOException {
    for (FeatureTypeState state : states.values()) {
      TMFeatureType featureType = state.featureType();
      Set<String> toDelete = new LinkedHashSet<>(state.removeOnRestore());
      state.saved().forEach(f -> toDelete.add(f.getID()));
      if (toDelete.isEmpty()) {
        continue;
      }
      DataStore ds = new JDBCFeatureSourceHelper().createDataStore(featureType.getFeatureSource());
      try (DefaultTransaction transaction = new DefaultTransaction("restore " + featureType.getName())) {
        SimpleFeatureStore store = (SimpleFeatureStore) ds.getFeatureSource(featureType.getName());
        store.setTransaction(transaction);
        try {
          store.removeFeatures(idFilter(toDelete));
          if (!state.saved().isEmpty()) {
            state.saved().forEach(f -> f.getUserData().put(Hints.USE_PROVIDED_FID, true));
            store.addFeatures(new ListFeatureCollection(store.getSchema(), state.saved()));
          }
          transaction.commit();
        } catch (IOException | RuntimeException e) {
          transaction.rollback();
          throw e;
        }
      } finally {
        ds.dispose();
      }
    }
    states.clear();
  }
}
