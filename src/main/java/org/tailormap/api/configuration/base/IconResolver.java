/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.configuration.base;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.resource.CachingResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;
import org.tailormap.api.persistence.Upload;
import org.tailormap.api.repository.UploadRepository;

@Service
public class IconResolver implements ResourceResolver, InitializingBean {
  private final UploadRepository uploadRepository;
  private CachingResourceResolver iconCachingResolver;

  @Value("${spring.profiles.active:}")
  private String activeProfile;

  private boolean staticOnly;

  public IconResolver(
      // Inject this repository lazily because in the static-only profile these are not needed
      // but also not configured
      @Lazy UploadRepository uploadRepository) {
    this.uploadRepository = uploadRepository;
  }

  @Override
  public void afterPropertiesSet() {
    this.staticOnly = activeProfile.contains("static-only");
  }

  public IconResolver setCachingResolver(CachingResourceResolver iconCachingResolver) {
    this.iconCachingResolver = iconCachingResolver;
    return this;
  }

  @Override
  public @Nullable Resource resolveResource(
      @Nullable HttpServletRequest request,
      @NonNull String requestPath,
      @NonNull List<? extends Resource> locations,
      @NonNull ResourceResolverChain chain) {
    if (staticOnly) {
      return chain.resolveResource(request, requestPath, locations);
    }

    String icon = StringUtils.substringAfterLast(requestPath, "/");
    Upload upload = uploadRepository
        .findWithContentByCategoryAndFilename(Upload.CATEGORY_THEME_FAVICON, icon)
        .orElse(null);
    if (upload != null) {
      return new ByteArrayResource(upload.getContent()) {
        @Override
        public String getFilename() {
          return upload.getFilename();
        }

        @Override
        public long lastModified() throws IOException {
          return Optional.ofNullable(upload.getLastModified())
              .map(lm -> lm.toEpochSecond() * 1000)
              .orElse(0L);
        }
      };
    } else {
      return chain.resolveResource(request, requestPath, locations);
    }
  }

  @Override
  public @Nullable String resolveUrlPath(
      @NonNull String resourcePath,
      @NonNull List<? extends Resource> locations,
      @NonNull ResourceResolverChain chain) {
    return chain.resolveUrlPath(resourcePath, locations);
  }

  public void clearCache() {
    if (this.iconCachingResolver != null) {
      this.iconCachingResolver.getCache().clear();
    }
  }
}
