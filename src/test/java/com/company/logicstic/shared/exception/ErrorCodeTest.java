package com.company.logicstic.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

@DisplayName("ErrorCode")
class ErrorCodeTest {

  @ParameterizedTest
  @EnumSource(ErrorCode.class)
  @DisplayName("every constant carries a failure status and a non-blank default message")
  void everyConstantIsUsable(ErrorCode errorCode) {
    assertThat(errorCode.getStatus().isError())
        .as("%s must map to a 4xx or 5xx status", errorCode)
        .isTrue();
    assertThat(errorCode.getDefaultMessage()).isNotBlank();
  }

  @ParameterizedTest
  @EnumSource(ErrorCode.class)
  @DisplayName("code() is the constant name, which clients switch on")
  void codeIsTheConstantName(ErrorCode errorCode) {
    assertThat(errorCode.code()).isEqualTo(errorCode.name());
  }

  @Test
  @DisplayName(
      "statuses match the catalogue in docs/docs/development/engineering-conventions.md §9")
  void statusesMatchTheDocumentedContract() {
    assertThat(ErrorCode.VALIDATION_FAILED.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(ErrorCode.INVALID_STATE_TRANSITION.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(ErrorCode.UNAUTHENTICATED.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(ErrorCode.ACCESS_DENIED.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(ErrorCode.RESOURCE_NOT_FOUND.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(ErrorCode.CONFLICT.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(ErrorCode.DATA_INTEGRITY_VIOLATION.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(ErrorCode.INTERNAL_ERROR.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @Test
  @DisplayName("the typed exceptions expose the code and status of their ErrorCode")
  void typedExceptionsInheritTheCatalogue() {
    assertThat(new ResourceNotFoundException("Load not found: 1"))
        .extracting(ApiException::getCode, ApiException::getStatus)
        .containsExactly(ErrorCode.RESOURCE_NOT_FOUND.code(), HttpStatus.NOT_FOUND);

    assertThat(new ConflictException("duplicate"))
        .extracting(ApiException::getCode, ApiException::getStatus)
        .containsExactly(ErrorCode.CONFLICT.code(), HttpStatus.CONFLICT);

    assertThat(new BadRequestException("bad"))
        .extracting(ApiException::getCode, ApiException::getStatus)
        .containsExactly(ErrorCode.BAD_REQUEST.code(), HttpStatus.BAD_REQUEST);

    assertThat(new InvalidStateTransitionException("Load", "Delivered", "Dispatched"))
        .extracting(ApiException::getCode, ApiException::getStatus)
        .containsExactly(ErrorCode.INVALID_STATE_TRANSITION.code(), HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("a feature-specific code keeps its own name but the catalogue's status")
  void featureSpecificCodeKeepsItsOwnName() {
    ConflictException exception =
        new ConflictException("TERMINAL_IN_USE", "Terminal 'BEANR' is referenced by loads");

    assertThat(exception.getCode()).isEqualTo("TERMINAL_IN_USE");
    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
  }
}
