/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.security;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotNull;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

public class OIDCRepository implements ClientRegistrationRepository, Iterable<ClientRegistration> {
  public static class OIDCRegistrationMetadata {
    private boolean showForViewer;

    public boolean getShowForViewer() {
      return showForViewer;
    }
  }

  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final OIDCConfigurationRepository oidcConfigurationRepository;

  @Value("${tailormap-api.oidc.name:#{null}}")
  private String oidcName;

  @Value("${tailormap-api.oidc.issuer-uri:#{null}}")
  private String oidcIssuerUri;

  @Value("${tailormap-api.oidc.client-id:#{null}}")
  private String oidcClientId;

  @Value("${tailormap-api.oidc.client-secret:#{null}}")
  private String oidcClientSecret;

  @Value("${tailormap-api.oidc.user-name-attribute:#{null}}")
  private String oidcUserNameAttribute;

  @Value("${tailormap-api.oidc.show-for-viewer:false}")
  private boolean oidcShowForViewer;

  private final Map<String, ClientRegistration> registrations;

  public OIDCRepository(OIDCConfigurationRepository repository) {
    oidcConfigurationRepository = repository;
    registrations = new HashMap<>();
  }

  @Override
  public ClientRegistration findByRegistrationId(String registrationId) {
    return registrations.get(registrationId);
  }

  @Override
  public @NotNull Iterator<ClientRegistration> iterator() {
    return registrations.values().iterator();
  }

  public OIDCRegistrationMetadata getMetadataForRegistrationId(String id) {
    OIDCRegistrationMetadata metadata = new OIDCRegistrationMetadata();
    if ("static".equals(id)) {
      metadata.showForViewer = oidcShowForViewer;
    } else {
      metadata.showForViewer = true;
    }

    return metadata;
  }

  @PostConstruct
  public void synchronize() {
    Map<String, ClientRegistration> newMap = new HashMap<>();

    final HttpClient httpClient =
        HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    for (OIDCConfiguration configuration : oidcConfigurationRepository.findAll()) {
      String id = String.format("%d", configuration.getId());
      try {
        HttpRequest.Builder requestBuilder =
            HttpRequest.newBuilder()
                .uri(new URI(configuration.getIssuerUrl() + "/.well-known/openid-configuration"));
        HttpResponse<String> response =
            httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        OIDCProviderMetadata metadata = OIDCProviderMetadata.parse(response.body());

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
        if (configuration.getStatus() != null) {
          configuration.setStatus(null);
          oidcConfigurationRepository.save(configuration);
        }
      } catch (Exception e) {
        logger.error("Failed to create OIDC client registration for ID " + id, e);
        configuration.setStatus(e.toString());
        oidcConfigurationRepository.save(configuration);
      }
    }

    if (isNotBlank(oidcName) && isNotBlank(oidcIssuerUri) && isNotBlank(oidcClientId)) {
      try {
        // When copying the URI from some IdP control panels into an .env file, this suffix won't be
        // stripped by OIDCConfigurationEventHandler.handleBeforeCreateOrSave() so accept both
        if (!oidcIssuerUri.endsWith("/.well-known/openid-configuration")) {
          oidcIssuerUri = oidcIssuerUri + "/.well-known/openid-configuration";
        }
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(new URI(oidcIssuerUri));
        HttpResponse<String> response =
            httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        OIDCProviderMetadata metadata = OIDCProviderMetadata.parse(response.body());
        String id = "static";

        newMap.put(
            id,
            ClientRegistration.withRegistrationId(id)
                .clientId(oidcClientId)
                .clientSecret(oidcClientSecret)
                .clientName(oidcName)
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
                .userNameAttributeName(oidcUserNameAttribute)
                .redirectUri("{baseUrl}/api/oauth2/callback")
                .build());
      } catch (Exception e) {
        logger.error("Failed to create static OIDC client registration", e);
      }
    }

    registrations.clear();
    registrations.putAll(newMap);
  }
}
