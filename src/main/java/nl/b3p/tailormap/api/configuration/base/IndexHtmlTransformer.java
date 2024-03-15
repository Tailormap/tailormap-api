/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.configuration.base;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.resource.ResourceTransformer;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

@Component
public class IndexHtmlTransformer implements ResourceTransformer, EnvironmentAware {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private Environment environment;

  @Override
  public void setEnvironment(@NonNull Environment environment) {
    this.environment = environment;
  }

  @Override
  @NonNull
  public Resource transform(
      @NonNull HttpServletRequest request,
      @NonNull Resource resource,
      @NonNull ResourceTransformerChain transformerChain)
      throws IOException {
    // Note that caching is not required because of cacheResources param to resourceChain() in
    // WebMvcConfig
    resource = transformerChain.transform(request, resource);
    String html = IOUtils.toString(resource.getInputStream(), UTF_8);
    String sentryDsn = environment.getProperty("VIEWER_SENTRY_DSN");
    if (isNotBlank(sentryDsn)) {
      logger.info("Sending Sentry DSN {} for URI {}", sentryDsn, request.getRequestURI());
      html = html.replace("@SENTRY_DSN@", sentryDsn);
    }
    return new TransformedResource(resource, html.getBytes(UTF_8));
  }
}
