/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.collection;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.collection.DecoratingSimpleFeatureIterator;
import org.jspecify.annotations.Nullable;

/** A decorating feature iterator that will call a callback after a specified number of features are handled. */
public class ProgressReportingFeatureIterator extends DecoratingSimpleFeatureIterator {

  private final AtomicInteger count = new AtomicInteger(0);
  private final int progressInterval;
  private final IntConsumer progressCallback;
  private SimpleFeatureIterator iterator;

  /**
   * Creates an iterator that reports progress after every configured number of processed features.
   *
   * @param iterator the wrapped feature iterator, must not be {@code null}
   * @param progressInterval the number of processed features between progress updates, must be greater than {@code 0}
   * @param progressCallback the callback that receives the current processed feature count; may be {@code null}
   * @throws IllegalArgumentException if {@code progressInterval <= 0}
   */
  public ProgressReportingFeatureIterator(
      SimpleFeatureIterator iterator, int progressInterval, @Nullable IntConsumer progressCallback) {
    super(iterator);
    if (progressInterval <= 0) {
      throw new IllegalArgumentException("progressInterval must be greater than 0");
    }
    this.iterator = iterator;
    this.progressInterval = progressInterval;
    this.progressCallback = progressCallback;
  }

  @Override
  public SimpleFeature next() {
    if (count.incrementAndGet() % progressInterval == 0) {
      if (progressCallback != null) {
        progressCallback.accept(count.get());
      }
    }
    return iterator.next();
  }

  @Override
  public boolean hasNext() {
    return iterator.hasNext();
  }

  @Override
  public void close() {
    iterator.close();
    iterator = null;
    count.set(0);
  }
}
