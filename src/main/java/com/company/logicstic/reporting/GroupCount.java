package com.company.logicstic.reporting;

/**
 * A raw value distribution: how many rows carry each distinct value of a free-text column.
 *
 * <p>Reporting deliberately does not fold these into named buckets. Invoice {@code type}, truck
 * {@code status} and customer {@code status} are all plain text with no enforced vocabulary, so a
 * value this deployment has never seen is a real possibility — and the response should be able to
 * show it rather than drop it into an "other" that hides the fact that something new appeared.
 *
 * @param label the stored value, exactly as it appears in the database
 * @param count how many rows carry it
 */
public record GroupCount(String label, long count) {}
