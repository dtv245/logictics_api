package com.company.logicstic.integration.lark.event;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Component
public class LarkEventDeduplicator {
  private final JdbcTemplate jdbc;
  private final Clock clock;
  private final ObjectMapper json;
  private final LarkEventProcessingProperties processing;

  @Autowired
  public LarkEventDeduplicator(DataSource dataSource, ObjectMapper json, LarkEventProcessingProperties processing) {
    this(dataSource, json, processing, Clock.systemUTC());
  }

  public LarkEventDeduplicator(DataSource dataSource) {
    this(dataSource, Clock.systemUTC());
  }

  LarkEventDeduplicator(DataSource dataSource, Clock clock) {
    this(dataSource, JsonMapper.builder().build(), LarkEventProcessingProperties.defaults(), clock);
  }

  LarkEventDeduplicator(DataSource dataSource, ObjectMapper json,
      LarkEventProcessingProperties processing, Clock clock) {
    this.jdbc = new JdbcTemplate(dataSource);
    this.clock = clock;
    this.json = json;
    this.processing = processing;
  }

  /** The payload and dedupe identity commit in one INSERT, before HTTP ACK. */
  public boolean enqueue(String eventId, String eventType, JsonNode event) {
    var now = clock.instant();
    // Unfinished work is never removed merely because the dedupe TTL elapsed.
    jdbc.update("DELETE FROM lark_event_receipts WHERE event_id = ? AND status IN ('COMPLETE','DEAD') AND expires_at <= ?",
        eventId, Timestamp.from(now));
    try {
      jdbc.update("""
          INSERT INTO lark_event_receipts(event_id,claim_id,expires_at,event_type,payload_json,status,attempts,next_attempt_at)
          VALUES (?,?,?,?,?,'PENDING',0,?)
          """, eventId, UUID.randomUUID(), Timestamp.from(now.plus(Duration.ofHours(24))),
          eventType, json.writeValueAsString(event), Timestamp.from(now));
      return true;
    } catch (DuplicateKeyException duplicate) {
      return false;
    }
  }

  public List<String> dueEvents() {
    return jdbc.queryForList("""
        SELECT event_id FROM lark_event_receipts
        WHERE (status IN ('PENDING','RETRY') AND next_attempt_at <= ?)
           OR (status = 'PROCESSING' AND locked_until <= ?)
        ORDER BY next_attempt_at,event_id LIMIT ?
        """, String.class, Timestamp.from(clock.instant()), Timestamp.from(clock.instant()), processing.batchSize());
  }

  public Optional<Delivery> claimDelivery(String eventId) {
    var now = clock.instant();
    var owner = UUID.randomUUID();
    int claimed = jdbc.update("""
        UPDATE lark_event_receipts SET status='PROCESSING',claim_id=?,attempts=attempts+1,locked_until=?
        WHERE event_id=? AND ((status IN ('PENDING','RETRY') AND next_attempt_at <= ?)
           OR (status='PROCESSING' AND locked_until <= ?))
        """, owner, Timestamp.from(now.plus(processing.leaseDuration())), eventId,
        Timestamp.from(now), Timestamp.from(now));
    if (claimed == 0) return Optional.empty();
    return jdbc.query("""
        SELECT event_type,payload_json,attempts FROM lark_event_receipts
        WHERE event_id=? AND claim_id=? AND status='PROCESSING'
        """, (row, index) -> new Delivery(eventId, row.getString("event_type"), row.getString("payload_json"),
        owner, row.getInt("attempts")), eventId, owner).stream().findFirst();
  }

  public boolean renew(Delivery delivery) {
    return jdbc.update("""
        UPDATE lark_event_receipts SET locked_until=? WHERE event_id=? AND claim_id=? AND status='PROCESSING'
        """, Timestamp.from(clock.instant().plus(processing.leaseDuration())), delivery.eventId(), delivery.owner()) == 1;
  }

  public void complete(Delivery delivery) {
    var now = clock.instant();
    jdbc.update("""
        UPDATE lark_event_receipts SET status='COMPLETE',payload_json=NULL,locked_until=NULL,
        next_attempt_at=NULL,completed_at=?,expires_at=? WHERE event_id=? AND claim_id=? AND status='PROCESSING'
        """, Timestamp.from(now), Timestamp.from(now.plus(Duration.ofHours(24))), delivery.eventId(), delivery.owner());
  }

  public void failed(Delivery delivery) {
    var now = clock.instant();
    boolean dead = delivery.attempt() >= processing.maxAttempts();
    long multiplier = 1L << Math.min(20, Math.max(0, delivery.attempt() - 1));
    Duration delay = processing.initialRetryDelay().multipliedBy(multiplier);
    if (delay.compareTo(processing.maxRetryDelay()) > 0) delay = processing.maxRetryDelay();
    jdbc.update("""
        UPDATE lark_event_receipts SET status=?,locked_until=NULL,next_attempt_at=?,completed_at=?
        WHERE event_id=? AND claim_id=? AND status='PROCESSING'
        """, dead ? "DEAD" : "RETRY", dead ? null : Timestamp.from(now.plus(delay)),
        dead ? Timestamp.from(now) : null, delivery.eventId(), delivery.owner());
  }

  public int cleanup() {
    var now = Timestamp.from(clock.instant());
    var deadBefore = Timestamp.from(clock.instant().minus(processing.deadRetention()));
    List<String> expired = jdbc.queryForList("""
        SELECT event_id FROM lark_event_receipts
        WHERE (status='COMPLETE' AND expires_at <= ?) OR (status='DEAD' AND completed_at <= ?)
        ORDER BY event_id LIMIT ?
        """, String.class, now, deadBefore, processing.cleanupBatchSize());
    int removed = 0;
    for (String eventId : expired) {
      removed += jdbc.update("""
          DELETE FROM lark_event_receipts WHERE event_id=? AND
          ((status='COMPLETE' AND expires_at <= ?) OR (status='DEAD' AND completed_at <= ?))
          """, eventId, now, deadBefore);
    }
    return removed;
  }

  public Optional<Claim> claim(String eventId) {
    var now = clock.instant();
    jdbc.update("DELETE FROM lark_event_receipts WHERE event_id = ? AND expires_at <= ? AND status IN ('COMPLETE','DEAD')",
        eventId, Timestamp.from(now));
    var claim = new Claim(eventId, UUID.randomUUID());
    try {
      jdbc.update("INSERT INTO lark_event_receipts(event_id, claim_id, expires_at) VALUES (?, ?, ?)",
          eventId, claim.claimId(), Timestamp.from(now.plus(Duration.ofHours(24))));
      return Optional.of(claim);
    } catch (DuplicateKeyException duplicate) {
      return Optional.empty();
    }
  }

  public void release(Claim claim) {
    // An old/rejected submit must never delete another delivery's newer claim.
    jdbc.update("DELETE FROM lark_event_receipts WHERE event_id = ? AND claim_id = ?",
        claim.eventId(), claim.claimId());
  }

  public record Claim(String eventId, UUID claimId) {}

  // Only metadata is printable; event payloads can contain business data.
  public record Delivery(String eventId, String eventType, String payloadJson, UUID owner, int attempt) {
    @Override public String toString() { return "LarkEventDelivery[event_id=" + LarkEventDispatcher.safeLog(eventId) + "]"; }
  }
}
