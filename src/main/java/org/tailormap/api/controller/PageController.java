/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.controller;

import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import org.tailormap.api.annotation.AppRestController;
import org.tailormap.api.persistence.Application;
import org.tailormap.api.persistence.Configuration;
import org.tailormap.api.persistence.Page;
import org.tailormap.api.repository.ApplicationRepository;
import org.tailormap.api.repository.ConfigurationRepository;
import org.tailormap.api.repository.PageRepository;
import org.tailormap.api.security.AuthorizationService;
import org.tailormap.api.viewer.model.PageResponse;
import org.tailormap.api.viewer.model.PageTile;

@AppRestController
public class PageController {

  private final ConfigurationRepository configurationRepository;
  private final ApplicationRepository applicationRepository;
  private final AuthorizationService authorizationService;
  private final PageRepository pageRepository;

  public PageController(
      ConfigurationRepository configurationRepository,
      ApplicationRepository applicationRepository,
      AuthorizationService authorizationService,
      PageRepository pageRepository) {
    this.configurationRepository = configurationRepository;
    this.applicationRepository = applicationRepository;
    this.pageRepository = pageRepository;
    this.authorizationService = authorizationService;
  }

  @GetMapping(path = "${tailormap-api.base-path}/page")
  public PageResponse homePage() {
    Page page = null;
    try {
      String homePage = configurationRepository.get(Configuration.HOME_PAGE);
      if (homePage != null && !homePage.isEmpty()) {
        Long homePageId = Long.parseLong(homePage);
        page = pageRepository.findById(homePageId).orElse(null);
      }
    } catch (NumberFormatException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    if (page == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    return getPageResponse(page);
  }

  @GetMapping(
      path = {
        "${tailormap-api.base-path}/page/{name}",
      })
  public PageResponse page(@PathVariable(required = false) String name) {
    Page page = pageRepository.findByName(name);
    if (page == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    return getPageResponse(page);
  }

  private PageResponse getPageResponse(Page page) {
    PageResponse pageResponse = page.getPageResponse();
    List<PageTile> pageTiles = new ArrayList<>();
    page.getTiles()
        .forEach(
            tile -> {
              PageTile pageTile =
                  new PageTile()
                      .title(tile.getTitle())
                      .content(tile.getContent())
                      .image(tile.getImage())
                      .className(tile.getClassName())
                      .openInNewWindow(tile.getOpenInNewWindow());
              if (tile.getApplicationId() != null) {
                Application application =
                    applicationRepository.findById(tile.getApplicationId()).orElse(null);
                if (application != null
                    && (!tile.getFilterRequireAuthorization()
                        || authorizationService.mayUserRead(application))) {
                  pageTile.applicationUrl("/app/" + application.getName());
                  pageTile.setApplicationRequiresLogin(
                      !authorizationService.mayUserRead(application));
                  pageTiles.add(pageTile);
                }
              } else if (tile.getPageId() != null) {
                Page linkedPage = pageRepository.findById(tile.getPageId()).orElse(null);
                if (linkedPage != null) {
                  pageTile.pageUrl("/page/" + linkedPage.getName());
                  pageTiles.add(pageTile);
                }
              } else {
                pageTile.url(tile.getUrl());
                pageTiles.add(pageTile);
              }
            });
    pageResponse.tiles(pageTiles);
    return pageResponse;
  }
}
