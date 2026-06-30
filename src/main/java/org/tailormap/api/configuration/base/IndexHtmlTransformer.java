/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.configuration.base;

import static java.nio.charset.StandardCharsets.UTF_8;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.jspecify.annotations.NonNull;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.resource.ResourceTransformer;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

@Component
public class IndexHtmlTransformer implements ResourceTransformer {

  @Override
  @NonNull public Resource transform(
      @NonNull HttpServletRequest request,
      @NonNull Resource resource,
      @NonNull ResourceTransformerChain transformerChain)
      throws IOException {
    resource = transformerChain.transform(request, resource);

    if (!"index.html".equals(resource.getFilename())) {
      return resource;
    }

    String html = resource.getContentAsString(UTF_8);
    // here we could modify the index.html, e.g. replacing a token with a configuration value
    return new TransformedResource(resource, html.getBytes(UTF_8));
  }
}
