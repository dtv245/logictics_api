package com.company.logicstic.shared.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * The catalogue of error codes this API can return, and the HTTP status each one maps to.
 *
 * <p>Before this enum the codes were string literals scattered across {@code
 * GlobalExceptionHandler} and the {@link ApiException} subclasses, so nothing stopped two throw
 * sites from spelling the same failure differently and nothing listed the contract in one place.
 * {@code code()} is what reaches the client in {@code ApiResponse.code} — it is part of the API
 * contract, so renaming a constant is a breaking change for every client that switches on it.
 *
 * <p>Statuses follow {@code docs/docs/development/engineering-conventions.md} §9 and the error
 * catalogue in {@code docs/docs/project-specification.vi.md} §14.4.
 *
 * <p>Adding a failure mode means adding a constant here, not inventing a string at the throw site.
 */
@Getter
public enum ErrorCode {

  // ── 400 — the request itself is wrong ────────────────────────────────────────
  /** Generic invalid input that no more specific code covers. */
  BAD_REQUEST(HttpStatus.BAD_REQUEST, "Invalid request"),
  /** Bean Validation rejected one or more fields; the details are in {@code errors[]}. */
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed"),
  /** The body was absent, truncated, or not parseable as JSON. */
  MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Request body is missing or malformed"),
  /** A state machine refused the transition — see {@link InvalidStateTransitionException}. */
  INVALID_STATE_TRANSITION(HttpStatus.BAD_REQUEST, "Invalid state transition"),

  // ── 401 / 403 — the caller is wrong ──────────────────────────────────────────
  /** No token, an expired token, or a signature/issuer/audience mismatch. */
  UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication is required"),
  /** Authenticated, but the role or permission does not allow this operation. */
  ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access denied"),

  // ── 404 — nothing to act on inside the tenant ────────────────────────────────
  /** No entity with that id exists <em>in the current tenant</em>. */
  RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
  /** The URL itself does not map to any handler. */
  NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),

  // ── 409 — the request conflicts with persisted state ─────────────────────────
  /** A duplicate or a concurrent-modification conflict. */
  CONFLICT(HttpStatus.CONFLICT, "Request conflicts with the current state"),
  /** A database constraint rejected the write; the constraint name is never leaked. */
  DATA_INTEGRITY_VIOLATION(HttpStatus.CONFLICT, "Cannot complete request due to a data conflict"),

  // ── 500 — the server is wrong ────────────────────────────────────────────────
  /** Anything unhandled. The detail is logged server-side and never returned. */
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");

  private final HttpStatus status;
  private final String defaultMessage;

  ErrorCode(HttpStatus status, String defaultMessage) {
    this.status = status;
    this.defaultMessage = defaultMessage;
  }

  /** The wire value placed in {@code ApiResponse.code}. */
  public String code() {
    return name();
  }
}
