package com.company.logicstic.shared.exception;

/**
 * Thrown when an invalid state transition is attempted (e.g. trying to dispatch an
 * already-delivered load).
 */
public class InvalidStateTransitionException extends ApiException {

  private final String entityName;
  private final String currentState;
  private final String targetState;

  public InvalidStateTransitionException(
      String entityName, String currentState, String targetState) {
    super(
        ErrorCode.INVALID_STATE_TRANSITION,
        String.format(
            "Cannot transition %s from '%s' to '%s'", entityName, currentState, targetState));
    this.entityName = entityName;
    this.currentState = currentState;
    this.targetState = targetState;
  }

  public String getEntityName() {
    return entityName;
  }

  public String getCurrentState() {
    return currentState;
  }

  public String getTargetState() {
    return targetState;
  }
}
