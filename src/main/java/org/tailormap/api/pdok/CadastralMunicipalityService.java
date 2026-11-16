/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.pdok;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.tailormap.api.admin.model.CadastralMunicipality;

/**
 * In-memory cache of the kadastrale gemeenten (cadastral municipalities) list, built from PDOK by
 * {@link CadastralMunicipalityClient}.
 *
 * <p>The list is built lazily on the first request (or at startup when warm-up is enabled), because building it takes
 * about 1,250 requests to PDOK. Concurrent callers share a single build. When a list has been built, it is refreshed on
 * a schedule; when a refresh fails, the previous list is kept. Installations that never request the list never contact
 * PDOK.
 */
@Service
public class CadastralMunicipalityService {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  /** A successfully built list and the time it was fetched. */
  public record CadastralMunicipalities(List<CadastralMunicipality> municipalities, Instant fetchedAt) {}

  private final CadastralMunicipalityClient client;
  private final boolean enabled;
  private final boolean warmUpOnStartup;

  private final AtomicReference<@Nullable CadastralMunicipalities> cache = new AtomicReference<>();
  private final AtomicReference<@Nullable CompletableFuture<CadastralMunicipalities>> runningBuild =
      new AtomicReference<>();

  public CadastralMunicipalityService(
      CadastralMunicipalityClient client,
      @Value("${tailormap-api.pdok.cadastral-municipalities.enabled:true}") boolean enabled,
      @Value("${tailormap-api.pdok.cadastral-municipalities.warm-up-on-startup:false}") boolean warmUpOnStartup) {
    this.client = client;
    this.enabled = enabled;
    this.warmUpOnStartup = warmUpOnStartup;
  }

  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Get the cached list. If no list was built yet, build it now (or wait for a build that is already running).
   *
   * @return the cached list
   * @throws IOException if no list was built before and building it fails
   */
  public CadastralMunicipalities getCadastralMunicipalities() throws IOException {
    CadastralMunicipalities cached = cache.get();
    if (cached != null) {
      return cached;
    }
    try {
      return refresh().join();
    } catch (CompletionException e) {
      throw e.getCause() instanceof IOException ioException
          ? ioException
          : new IOException("Error building the list of cadastral municipalities", e.getCause());
    }
  }

  /**
   * Rebuild the list. If a build is already running, return that build instead of starting a second one. On success
   * the cache is replaced; on failure the previous list is kept and a warning is logged.
   *
   * @return a future that is completed when the build finishes, exceptionally when it fails
   */
  CompletableFuture<CadastralMunicipalities> refresh() {
    CompletableFuture<CadastralMunicipalities> build = new CompletableFuture<>();
    CompletableFuture<CadastralMunicipalities> existing = runningBuild.compareAndExchange(null, build);
    if (existing != null) {
      return existing;
    }
    try {
      CadastralMunicipalities result = new CadastralMunicipalities(client.fetchAll(), Instant.now());
      cache.set(result);
      build.complete(result);
    } catch (Exception e) {
      if (cache.get() != null) {
        logger.warn("Error refreshing the list of cadastral municipalities, keeping the previous list", e);
      } else {
        logger.warn("Error building the list of cadastral municipalities", e);
      }
      build.completeExceptionally(e);
    } finally {
      runningBuild.set(null);
    }
    return build;
  }

  /** Refresh the list periodically, but only if it was built before. */
  // A failed refresh is logged by refresh() and the previous list is kept
  @SuppressWarnings("FutureReturnValueIgnored")
  @Scheduled(cron = "${tailormap-api.pdok.cadastral-municipalities.refresh-cron:0 0 3 * * SUN}")
  public void scheduledRefresh() {
    if (enabled && cache.get() != null) {
      logger.info("Refreshing the list of cadastral municipalities from PDOK");
      refresh();
    }
  }

  /** Build the list in the background at startup, if enabled. */
  // A failed build is logged by refresh(); the first admin request then tries again
  @SuppressWarnings("FutureReturnValueIgnored")
  @EventListener(ApplicationReadyEvent.class)
  public void warmUp() {
    if (enabled && warmUpOnStartup) {
      Thread.ofVirtual().name("cadastral-municipalities-warm-up").start(this::refresh);
    }
  }
}
