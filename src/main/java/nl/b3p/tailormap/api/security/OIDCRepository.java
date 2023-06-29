/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.security;

import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import nl.b3p.tailormap.api.persistence.OIDCConfiguration;
import nl.b3p.tailormap.api.repository.OIDCConfigurationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

public class OIDCRepository implements ClientRegistrationRepository, Iterable<ClientRegistration> {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final OIDCConfigurationRepository oidcConfigurationRepository;

  private final Map<String, ClientRegistration> registrations;

  public OIDCRepository(OIDCConfigurationRepository repository) {
    oidcConfigurationRepository = repository;
    registrations = new HashMap<>();

    synchronize();
  }

  @Override
  public ClientRegistration findByRegistrationId(String registrationId) {
    return registrations.get(registrationId);
  }

  @Override
  public Iterator<ClientRegistration> iterator() {
    return registrations.values().iterator();
  }

  public void synchronize() {
    Map<String, ClientRegistration> newMap = new HashMap<>();

    final HttpClient httpClient =
        HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    for (OIDCConfiguration configuration : oidcConfigurationRepository.findAll()) {
      try {
        HttpRequest.Builder requestBuilder =
            HttpRequest.newBuilder()
                .uri(new URI(configuration.getIssuerUrl() + "/.well-known/openid-configuration"));
        HttpResponse<String> response =
            httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        OIDCProviderMetadata metadata = OIDCProviderMetadata.parse(response.body());

        String id = String.format("%d", configuration.getId());
        newMap.put(
            id,
            ClientRegistration.withRegistrationId(id)
                .clientId(configuration.getClientId())
                .clientSecret(configuration.getClientSecret())
                .clientName(configuration.getName())
                .scope("openid")
                .issuerUri(metadata.getIssuer().toString())
                .clientAuthenticationMethod(
                    ClientAuthenticationMethod
                        .CLIENT_SECRET_BASIC) // TODO: fetch from OIDC metadata
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationUri(metadata.getAuthorizationEndpointURI().toASCIIString())
                .tokenUri(metadata.getTokenEndpointURI().toASCIIString())
                .userInfoUri(metadata.getUserInfoEndpointURI().toASCIIString())
                .providerConfigurationMetadata(metadata.toJSONObject())
                .jwkSetUri(metadata.getJWKSetURI().toASCIIString())
                .userNameAttributeName(configuration.getUserNameAttribute())
                .redirectUri("{baseUrl}/api/oauth2/callback")
                .build());
      } catch (Exception e) {
        logger.error("Failed to create OIDC client registration", e);
      }
    }

    registrations.clear();
    registrations.putAll(newMap);
  }
}
