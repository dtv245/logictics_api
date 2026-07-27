package com.company.logicstic.modules.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "api_keys", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class ApiKey {

  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "\"name\"", nullable = false, length = 128)
  private String name;

  @Column(name = "key_hash", nullable = false, unique = true, length = 128)
  private String keyHash;

  @Column(name = "key_prefix", nullable = false, length = 64)
  private String keyPrefix;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "last_used_at")
  private OffsetDateTime lastUsedAt;
}
