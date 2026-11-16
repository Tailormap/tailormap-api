/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.pdok;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.tailormap.api.admin.model.CadastralMunicipality;

class CadastralMunicipalityServiceTest {
  private static final List<CadastralMunicipality> LIST_1 =
      List.of(new CadastralMunicipality().code("ATN02").name("Aalten"));
  private static final List<CadastralMunicipality> LIST_2 = List.of(
      new CadastralMunicipality().code("ATN02").name("Aalten"),
      new CadastralMunicipality().code("ABG00").name("Aalburg"));

  private final CadastralMunicipalityClient client = mock(CadastralMunicipalityClient.class);

  @Test
  void first_call_builds_the_list_and_next_calls_use_the_cache() throws IOException {
    when(client.fetchAll()).thenReturn(LIST_1);
    CadastralMunicipalityService service = new CadastralMunicipalityService(client, true, false);

    assertEquals(LIST_1, service.getCadastralMunicipalities().municipalities());
    assertEquals(LIST_1, service.getCadastralMunicipalities().municipalities());
    verify(client, times(1)).fetchAll();
  }

  @Test
  void concurrent_first_calls_share_one_build() throws Exception {
    CountDownLatch buildStarted = new CountDownLatch(1);
    CountDownLatch releaseBuild = new CountDownLatch(1);
    when(client.fetchAll()).thenAnswer(invocation -> {
      buildStarted.countDown();
      assertTrue(releaseBuild.await(10, TimeUnit.SECONDS));
      return LIST_1;
    });
    CadastralMunicipalityService service = new CadastralMunicipalityService(client, true, false);

    List<CadastralMunicipalityService.CadastralMunicipalities> results =
        Collections.synchronizedList(new ArrayList<>());
    Runnable caller = () -> {
      try {
        results.add(service.getCadastralMunicipalities());
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    };
    Thread first = Thread.ofPlatform().start(caller);
    assertTrue(buildStarted.await(10, TimeUnit.SECONDS));
    List<Thread> waiting = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      waiting.add(Thread.ofPlatform().start(caller));
    }
    // wait until the other callers are blocked waiting for the running build
    await().atMost(Duration.ofSeconds(10))
        .until(() -> waiting.stream().allMatch(t -> t.getState() == Thread.State.WAITING));
    releaseBuild.countDown();
    first.join();
    for (Thread t : waiting) {
      t.join();
    }

    verify(client, times(1)).fetchAll();
    assertEquals(5, results.size());
    results.forEach(r -> assertSame(results.getFirst(), r));
  }

  @Test
  void refresh_replaces_the_list() throws IOException {
    when(client.fetchAll()).thenReturn(LIST_1).thenReturn(LIST_2);
    CadastralMunicipalityService service = new CadastralMunicipalityService(client, true, false);
    service.getCadastralMunicipalities();

    service.scheduledRefresh();

    assertEquals(LIST_2, service.getCadastralMunicipalities().municipalities());
  }

  @Test
  void failed_refresh_keeps_the_previous_list() throws IOException {
    when(client.fetchAll()).thenReturn(LIST_1).thenThrow(new IOException("PDOK down"));
    CadastralMunicipalityService service = new CadastralMunicipalityService(client, true, false);
    service.getCadastralMunicipalities();

    service.scheduledRefresh();

    verify(client, times(2)).fetchAll();
    assertEquals(LIST_1, service.getCadastralMunicipalities().municipalities());
  }

  @Test
  void failed_first_build_throws_and_the_next_call_tries_again() throws IOException {
    when(client.fetchAll()).thenThrow(new IOException("PDOK down")).thenReturn(LIST_1);
    CadastralMunicipalityService service = new CadastralMunicipalityService(client, true, false);

    IOException e = assertThrows(IOException.class, service::getCadastralMunicipalities);
    assertEquals("PDOK down", e.getMessage());
    assertEquals(LIST_1, service.getCadastralMunicipalities().municipalities());
  }

  @Test
  void runtime_exception_during_build_is_thrown_as_io_exception() throws IOException {
    when(client.fetchAll()).thenThrow(new IllegalStateException("unexpected"));
    CadastralMunicipalityService service = new CadastralMunicipalityService(client, true, false);

    assertThrows(IOException.class, service::getCadastralMunicipalities);
  }

  @Test
  void scheduled_refresh_does_nothing_when_the_list_was_never_built() {
    CadastralMunicipalityService service = new CadastralMunicipalityService(client, true, false);

    service.scheduledRefresh();

    verifyNoInteractions(client);
  }

  @Test
  void scheduled_refresh_and_warm_up_do_nothing_when_disabled() {
    CadastralMunicipalityService service = new CadastralMunicipalityService(client, false, true);

    service.warmUp();
    service.scheduledRefresh();

    verifyNoInteractions(client);
  }

  @Test
  void warm_up_builds_the_list_in_the_background() throws IOException {
    when(client.fetchAll()).thenReturn(LIST_1);
    CadastralMunicipalityService service = new CadastralMunicipalityService(client, true, true);

    service.warmUp();

    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> verify(client, times(1)).fetchAll());
    assertEquals(LIST_1, service.getCadastralMunicipalities().municipalities());
    verify(client, times(1)).fetchAll();
  }
}
