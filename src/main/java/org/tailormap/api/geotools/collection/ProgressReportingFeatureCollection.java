/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.collection;

import java.util.function.IntConsumer;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.collection.DecoratingSimpleFeatureCollection;
import org.jspecify.annotations.Nullable;

/**
 * A decorating feature collection that will pass a callback to the iterator to report the number of features provided.
 */
public class ProgressReportingFeatureCollection extends DecoratingSimpleFeatureCollection {
  private final int progressInterval;
  private final IntConsumer progressCallback;

  /**
   * Creates a new {@code ProgressReportingFeatureCollection} that wraps the given delegate and reports progress at
   * the specified interval.
   *
   * @param delegate the underlying {@link SimpleFeatureCollection} to decorate
   * @param progressInterval the number of features between each progress callback invocation; must be greater than
   *     {@code 0}
   * @param progressCallback a callback that receives the current feature count at each interval; may be {@code null}
   */
  public ProgressReportingFeatureCollection(
      SimpleFeatureCollection delegate, int progressInterval, @Nullable IntConsumer progressCallback) {
    super(delegate);
    if (progressInterval <= 0) {
      throw new IllegalArgumentException("progressInterval must be greater than 0");
    }
    this.delegate = delegate;
    this.progressInterval = progressInterval;
    this.progressCallback = progressCallback;
  }

  @Override
  public SimpleFeatureIterator features() {
    return new ProgressReportingFeatureIterator(delegate.features(), progressInterval, progressCallback);
  }
}
