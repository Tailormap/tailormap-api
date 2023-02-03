/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Timed;
import java.io.Serializable;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import javax.persistence.EntityNotFoundException;
import nl.b3p.tailormap.api.annotation.AppRestController;
import nl.b3p.tailormap.api.model.AppResponse;
import nl.b3p.tailormap.api.model.AppStyling;
import nl.b3p.tailormap.api.model.Component;
import nl.b3p.tailormap.api.repository.ApplicationRepository;
import nl.b3p.tailormap.api.repository.ConfigurationRepository;
import nl.b3p.tailormap.api.security.AuthorizationService;
import nl.tailormap.viewer.config.app.Application;
import nl.tailormap.viewer.config.metadata.Metadata;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@AppRestController
@CrossOrigin
@RequestMapping(path = "/app")
public class AppController {
  private final Log logger = LogFactory.getLog(getClass());

  private final String COMPONENTS_CONFIG_KEY = "components";

  private final String STYLING_CONFIG_KEY = "application_style";

  private final ApplicationRepository applicationRepository;
  private final ConfigurationRepository configurationRepository;
  private final AuthorizationService authorizationService;

  @Value("${tailormap-api.apiVersion}")
  private String apiVersion;

  public AppController(
      ApplicationRepository applicationRepository,
      ConfigurationRepository configurationRepository,
      AuthorizationService authorizationService) {
    this.applicationRepository = applicationRepository;
    this.configurationRepository = configurationRepository;
    this.authorizationService = authorizationService;
  }

  /**
   * Lookup an {@linkplain Application} with given parameters. Use this endpoint to get the id of
   * the requested or default application. Either call this with `name` and optional `version` or
   * `appId` alone. Will return general setup information such as name, appId, language, but not map
   * specific information, may return a redirect response for login.
   *
   * @param appId the unique identifier of an app
   * @param name the name of an app
   * @param version the version of an app
   * @return the basic information needed to create an app in the frontend
   * @since 0.1
   */
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Timed(value = "get_app", description = "time spent to find an app")
  public ResponseEntity<Serializable> get(
      @RequestParam(required = false) Long appId,
      @RequestParam(required = false) String name,
      @RequestParam(required = false) String version) {

    logger.trace(
        "requested application using: appId: "
            + appId
            + ", name: "
            + name
            + ", version: "
            + version);

    Application application;
    if (null != appId) {
      application = applicationRepository.findById(appId).orElse(null);
    } else {
      application = findApplication(name, version);
    }

    if (null == application) {
      application = getDefaultViewer();
    }

    if (null == application) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Requested application not found and no default application set");
    } else if (!authorizationService.mayUserRead(application)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    } else {
      logger.trace(
          "found application - id:"
              + application.getId()
              + ", name: "
              + application.getName()
              + ", version: "
              + application.getVersion()
              + ", lang: "
              + application.getLang()
              + ", title: "
              + application.getTitle());

      Component[] components;
      try {
        String json =
            Optional.ofNullable(application.getDetails().get(COMPONENTS_CONFIG_KEY))
                .map(Object::toString)
                .orElse("[]");
        components = new ObjectMapper().readValue(json, Component[].class);
      } catch (JacksonException je) {
        throw new ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR, "Invalid components JSON", je);
      }

      AppStyling style;
      try {
        String json =
            Optional.ofNullable(application.getDetails().get(STYLING_CONFIG_KEY))
                .map(Object::toString)
                .orElse("{}");
        style = new ObjectMapper().readValue(json, AppStyling.class);
      } catch (JacksonException je) {
        throw new ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR, "Invalid app style JSON", je);
      }

      AppResponse appResponse =
          new AppResponse()
              .apiVersion(this.apiVersion)
              .id(application.getId())
              .name(application.getName())
              // any of these 2 below + language could be null
              .version(application.getVersion())
              .title(application.getTitle())
              .styling(style)
              .components(List.of(components));

      // null check language because it's an enumerated value
      if (null != application.getLang())
        appResponse.lang(AppResponse.LangEnum.fromValue(application.getLang()));

      return ResponseEntity.ok(appResponse);
    }
  }

  /**
   * recursive method to find application by name and version.
   *
   * @param name user given name
   * @param version user given version (can be {@code null})
   * @return found Application (or {@code null} if nothing found)
   */
  private Application findApplication(String name, String version) {
    Application application = null;
    if (name != null) {
      if (null != version) {
        if (version.startsWith("v")) {
          // In links to applications with version, a 'v' is added before the actual
          // version entered by an app admin
          version = version.substring(1);
        }
        application = applicationRepository.findByNameAndVersion(name, version);
      } else {
        application =
            applicationRepository.findByName(name).stream().sorted().findFirst().orElse(null);
      }

      if (null == application) {
        logger.warn("No application found with name " + name + " and version " + version);
        String decodedName = URLDecoder.decode(name, StandardCharsets.UTF_8);
        if (!decodedName.equals(name)) {
          return findApplication(decodedName, version);
        }
      }
    }
    return application;
  }

  /**
   * find the default viewer (name and version) in this instance.
   *
   * @return either the default Application or {@code null} if not configured
   */
  private Application getDefaultViewer() {
    try {
      Metadata md = configurationRepository.findByConfigKey(Metadata.DEFAULT_APPLICATION);
      String appId = md.getConfigValue();
      Long id = Long.parseLong(appId);
      return applicationRepository.getReferenceById(id);
    } catch (NullPointerException | EntityNotFoundException e) {
      logger.warn("No default application configured. " + e.getMessage());
      return null;
    }
  }
}
