/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.poc.webapp.security.oauth;

import static io.camunda.poc.webapp.security.BaseWebConfigurer.sendJSONErrorMessage;
import static io.camunda.poc.webapp.security.WebAppPocProfileService.IDENTITY_AUTH_PROFILE;

import io.camunda.identity.sdk.IdentityConfiguration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

@Component
@Profile(IDENTITY_AUTH_PROFILE)
public class IdentityOAuth2WebConfigurer {

  public static final String SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_ISSUER_URI =
      "spring.security.oauth2.resourceserver.jwt.issuer-uri";
  // Where to find the public key to validate signature,
  // which was created from authorization server's private key
  public static final String SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_JWK_SET_URI =
      "spring.security.oauth2.resourceserver.jwt.jwk-set-uri";

  public static final String JWKS_PATH = "/protocol/openid-connect/certs";

  private static final Logger LOGGER = LoggerFactory.getLogger(IdentityOAuth2WebConfigurer.class);

  @Autowired private Environment env;

  @Autowired private IdentityConfiguration identityConfiguration;

  @Autowired private IdentityJwt2AuthenticationTokenConverter jwtConverter;

  public void configure(final HttpSecurity http) throws Exception {
    if (isJWTEnabled()) {
      http.oauth2ResourceServer(
          serverCustomizer ->
              serverCustomizer
                  .authenticationEntryPoint(this::authenticationFailure)
                  .jwt(
                      jwtCustomizer ->
                          jwtCustomizer
                              .jwtAuthenticationConverter(jwtConverter)
                              .jwkSetUri(getJwkSetUriProperty())));
      LOGGER.info("Enabled OAuth2 JWT access to Tasklist API");
    }
  }

  private String getJwkSetUriProperty() {
    final String backendUri;

    // If the SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_JWK_SET_URI is present, then it has already
    // been correctly
    // calculated and should be used as-is.
    if (env.containsProperty(SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_JWK_SET_URI)) {
      backendUri = env.getProperty(SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_JWK_SET_URI);
      LOGGER.info(
          "Using value in SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_JWK_SET_URI for issuer authentication");
    } else {
      backendUri = identityConfiguration.getIssuerBackendUrl() + JWKS_PATH;
      LOGGER.warn(
          "SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_JWK_SET_URI is not present, building issuer authentication uri from issuer backend url.");
    }

    LOGGER.info("Using {} for issuer authentication", backendUri);

    return backendUri;
  }

  private void authenticationFailure(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final AuthenticationException e)
      throws IOException {
    sendJSONErrorMessage(response, e.getMessage());
  }

  protected boolean isJWTEnabled() {
    return env.containsProperty(SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_ISSUER_URI)
        || env.containsProperty(SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_JWK_SET_URI);
  }
}
