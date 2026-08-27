package com.company.logicstic.modules.load.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.logicstic.modules.identity.service.CurrentUserService;
import com.company.logicstic.modules.load.dto.response.LoadResponse;
import com.company.logicstic.modules.load.service.LoadService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class LoadControllerTest {

  @Test
  void pickUpForwardsOnlyJwtResolvedEmployeeId() {
    LoadService loadService = mock(LoadService.class);
    CurrentUserService currentUserService = mock(CurrentUserService.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    JwtAuthenticationToken authentication = authentication();
    UUID loadId = UUID.randomUUID();
    UUID employeeId = UUID.randomUUID();
    LoadResponse response = mock(LoadResponse.class);
    when(currentUserService.requireCurrentEmployeeId(authentication)).thenReturn(employeeId);
    when(loadService.pickUp(loadId, employeeId)).thenReturn(response);

    var result =
        new LoadController(loadService, currentUserService).pickUp(loadId, authentication, request);

    assertThat(result.getStatusCode().value()).isEqualTo(200);
    verify(currentUserService).requireCurrentEmployeeId(authentication);
    verify(loadService).pickUp(loadId, employeeId);
  }

  @Test
  void deliverForwardsOnlyJwtResolvedEmployeeId() {
    LoadService loadService = mock(LoadService.class);
    CurrentUserService currentUserService = mock(CurrentUserService.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    JwtAuthenticationToken authentication = authentication();
    UUID loadId = UUID.randomUUID();
    UUID employeeId = UUID.randomUUID();
    LoadResponse response = mock(LoadResponse.class);
    when(currentUserService.requireCurrentEmployeeId(authentication)).thenReturn(employeeId);
    when(loadService.deliver(loadId, employeeId)).thenReturn(response);

    var result =
        new LoadController(loadService, currentUserService)
            .deliver(loadId, authentication, request);

    assertThat(result.getStatusCode().value()).isEqualTo(200);
    verify(currentUserService).requireCurrentEmployeeId(authentication);
    verify(loadService).deliver(loadId, employeeId);
  }

  private static JwtAuthenticationToken authentication() {
    Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("subject").build();
    return new JwtAuthenticationToken(jwt);
  }
}
