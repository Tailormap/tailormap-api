/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.persistence.listener;

import static ch.rasc.sse.eventbus.SseEvent.DEFAULT_EVENT;
import static nl.b3p.tailormap.api.admin.model.ServerSentEvent.EventTypeEnum.ENTITY_CREATED;
import static nl.b3p.tailormap.api.admin.model.ServerSentEvent.EventTypeEnum.ENTITY_DELETED;
import static nl.b3p.tailormap.api.admin.model.ServerSentEvent.EventTypeEnum.ENTITY_UPDATED;

import ch.rasc.sse.eventbus.SseEvent;
import ch.rasc.sse.eventbus.SseEventBus;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import java.lang.invoke.MethodHandles;
import nl.b3p.tailormap.api.admin.model.EntityEvent;
import nl.b3p.tailormap.api.admin.model.ServerSentEvent;
import nl.b3p.tailormap.api.persistence.TMFeatureType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.rest.webmvc.config.RepositoryRestMvcConfiguration;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;

@Component
public class EntityEventPublisher {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Autowired @Lazy private EntityManagerFactory entityManagerFactory;

  @Autowired @Lazy private ObjectMapper objectMapper;

  @Autowired @Lazy private SseEventBus eventBus;

  @Autowired @Lazy private RepositoryRestMvcConfiguration repositoryRestMvcConfiguration;

  public EntityEventPublisher() {}

  private void sendEvent(
      ServerSentEvent.EventTypeEnum eventTypeEnum, Object entity, boolean serializeEntity) {
    Object id = null;

    if (RequestContextHolder.getRequestAttributes() == null) {
      // No current request -- do not send events / serialize entities during app startup
      return;
    }

    try {
      id = entityManagerFactory.getPersistenceUnitUtil().getIdentifier(entity);
      EntityEvent entityEvent =
          new EntityEvent().entityName(entity.getClass().getSimpleName()).id(String.valueOf(id));
      if (serializeEntity) {
        entityEvent.setObject(repositoryRestMvcConfiguration.objectMapper().valueToTree(entity));
      }
      ServerSentEvent event = new ServerSentEvent().eventType(eventTypeEnum).details(entityEvent);
      this.eventBus.handleEvent(SseEvent.of(DEFAULT_EVENT, objectMapper.writeValueAsString(event)));
    } catch (Exception e) {
      logger.error(
          "Error sending SSE for event type {}, entity {}, id {}",
          eventTypeEnum,
          entity != null ? entity.getClass().getSimpleName() : null,
          id,
          e);
    }
  }

  @PostPersist
  public void postPersist(Object entity) {
    // Feature types are created when TMFeatureSource is created, so do not send event
    // Note that when TMFeatureSource created event is sent, the oneToMany allFeatureTypes
    // have no ID's yet. So for a created feature source the frontend must retrieve the
    // TMFeatureSource separately or use the return value of the POST request.
    if (!(entity instanceof TMFeatureType)) {
      sendEvent(ENTITY_CREATED, entity, true);
    }
  }

  @PostRemove
  public void postRemove(Object entity) {
    // Feature types are only deleted when refreshing the feature source, so the updated/deleted
    // event for TMFeatureSource suffices.
    if (!(entity instanceof TMFeatureType)) {
      sendEvent(ENTITY_DELETED, entity, false);
    }
  }

  @PostUpdate
  public void postUpdate(Object entity) {
    // Note that for an updated TMFeatureSource, new TMFeatureTypes do appear to have ID's set.
    sendEvent(ENTITY_UPDATED, entity, true);
  }
}
