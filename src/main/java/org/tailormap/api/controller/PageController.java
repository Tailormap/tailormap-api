/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.controller;

import java.util.Optional;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import org.tailormap.api.annotation.AppRestController;
import org.tailormap.api.persistence.Configuration;
import org.tailormap.api.persistence.Page;
import org.tailormap.api.persistence.Upload;
import org.tailormap.api.persistence.helper.UploadHelper;
import org.tailormap.api.persistence.json.PageTile;
import org.tailormap.api.repository.ApplicationRepository;
import org.tailormap.api.repository.ConfigurationRepository;
import org.tailormap.api.repository.PageRepository;
import org.tailormap.api.security.AuthorizationService;
import org.tailormap.api.viewer.model.PageResponse;
import org.tailormap.api.viewer.model.ViewerPageTile;

@AppRestController
public class PageController {

  private final ConfigurationRepository configurationRepository;
  private final ApplicationRepository applicationRepository;
  private final AuthorizationService authorizationService;
  private final PageRepository pageRepository;
  private final UploadHelper uploadHelper;

  public PageController(
      ConfigurationRepository configurationRepository,
      ApplicationRepository applicationRepository,
      AuthorizationService authorizationService,
      PageRepository pageRepository,
      UploadHelper uploadHelper) {
    this.configurationRepository = configurationRepository;
    this.applicationRepository = applicationRepository;
    this.pageRepository = pageRepository;
    this.authorizationService = authorizationService;
    this.uploadHelper = uploadHelper;
  }

  private ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND);
  }

  @GetMapping(path = "${tailormap-api.base-path}/page")
  public PageResponse homePage() {
    Page page =
        configurationRepository
            .findByKey(Configuration.HOME_PAGE)
            .map(Configuration::getValue)
            .map(Long::parseLong)
            .flatMap(pageRepository::findById)
            .orElseThrow(this::notFound);
    return getPageResponse(page);
  }

  @GetMapping(path = {"${tailormap-api.base-path}/page/{name}"})
  public PageResponse page(@PathVariable(required = false) String name) {
    return pageRepository.findByName(name).map(this::getPageResponse).orElseThrow(this::notFound);
  }

  private static class ViewerPageTileResult {
    ViewerPageTile viewerPageTile;
    boolean shouldBeFiltered;
  }

  private PageResponse getPageResponse(Page page) {
    PageResponse pageResponse = new PageResponse();
    BeanUtils.copyProperties(page, pageResponse);
    pageResponse.tiles(
        page.getTiles().stream()
            .map(this::convert)
            .filter(viewerPageTileResult -> !viewerPageTileResult.shouldBeFiltered)
            .map(viewerPageTileResult -> viewerPageTileResult.viewerPageTile)
            .toList());
    return pageResponse;
  }

  /**
   * @param tile The page tile configuration
   * @return A page tile for the viewer, or null if the tile should not be shown (filtered).
   */
  private ViewerPageTileResult convert(PageTile tile) {
    ViewerPageTile viewerPageTile = new ViewerPageTile();
    ViewerPageTileResult result = new ViewerPageTileResult();
    result.viewerPageTile = viewerPageTile;
    result.shouldBeFiltered = false;
    BeanUtils.copyProperties(tile, viewerPageTile);

    Optional.ofNullable(tile.getApplicationId())
        .flatMap(applicationRepository::findById)
        .filter(
            application ->
                !Boolean.TRUE.equals(tile.getFilterRequireAuthorization())
                    || authorizationService.mayUserRead(application))
        .ifPresentOrElse(
            application -> {
              viewerPageTile.applicationUrl("/app/" + application.getName());
              viewerPageTile.setApplicationRequiresLogin(
                  !authorizationService.mayUserRead(application));
            },
            () -> result.shouldBeFiltered = true);

    Optional.ofNullable(tile.getPageId())
        .flatMap(pageRepository::findById)
        .ifPresent(
            linkedPage -> {
              viewerPageTile.pageUrl("/page/" + linkedPage.getName());
            });

    viewerPageTile.image(
        uploadHelper.getUrlForImage(tile.getImage(), Upload.CATEGORY_PORTAL_IMAGE));

    // No application or page, just manual (external) url
    return result;
  }
}
