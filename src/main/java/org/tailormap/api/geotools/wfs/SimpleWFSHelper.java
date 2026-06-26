/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.wfs;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

public class SimpleWFSHelper {
  public static final String DEFAULT_WFS_VERSION = "1.1.0";

  public static URI getWFSRequestURL(
      String wfsUrl, String request, String version, MultiValueMap<String, String> parameters) {
    return getOGCRequestURL(wfsUrl, "WFS", version, request, parameters);
  }

  public static URI getOGCRequestURL(
      String url, String service, String version, String request, MultiValueMap<String, String> parameters) {

    final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("SERVICE", service);
    params.add("VERSION", version);
    params.add("REQUEST", request);
    if (parameters != null) {
      // We need to encode the parameters manually because UriComponentsBuilder annoyingly does not
      // encode '+' as used in mime types for output formats, see
      // https://stackoverflow.com/questions/18138011
      parameters.replaceAll((key, values) -> values.stream()
          .map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8))
          .collect(Collectors.toList()));
      params.addAll(parameters);
    }
    return UriComponentsBuilder.fromUriString(url)
        .replaceQueryParams(params)
        .build(true)
        .toUri();
  }
}
