package org.camunda.optimize.service.security;

import org.camunda.optimize.dto.optimize.CredentialsDto;
import org.camunda.optimize.service.exceptions.UnauthorizedUserException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @author Askar Akhmerov
 */
@Component
public class AuthenticationService {
  @Resource (name = "elasticAuthenticationProvider")
  private AuthenticationProvider elasticAuthenticationProvider;
  @Resource (name = "engineAuthenticationProvider")
  private AuthenticationProvider engineAuthenticationProvider;

  @Autowired
  private TokenService tokenService;

  public String authenticateUser(CredentialsDto credentials) throws UnauthorizedUserException {
    if (!engineAuthenticationProvider.authenticate(credentials.getUsername(), credentials.getPassword())) {
      // Authenticate the user using the credentials provided
      if (!elasticAuthenticationProvider.authenticate(credentials.getUsername(), credentials.getPassword())) {
        throw new UnauthorizedUserException();
      }
    }

    // Issue a token for the user
    String token = tokenService.issueToken(credentials.getUsername());
    return token;
  }
}
