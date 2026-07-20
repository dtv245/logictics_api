package com.company.logicstic.modules.fleet.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "eld_provider_configurations", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class EldProviderConfiguration {

  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "provider_type", nullable = false, unique = true, columnDefinition = "text")
  private String providerType;

  @Column(name = "api_key", nullable = false, length = 500)
  private String apiKey;

  @Column(name = "api_secret", length = 500)
  private String apiSecret;

  @Column(name = "access_token", length = 2000)
  private String accessToken;

  @Column(name = "refresh_token", length = 2000)
  private String refreshToken;

  @Column(name = "token_expires_at")
  private OffsetDateTime tokenExpiresAt;

  @Column(name = "webhook_secret", length = 500)
  private String webhookSecret;

  @Column(name = "is_active", nullable = false)
  private Boolean isActive;

  @Column(name = "last_synced_at")
  private OffsetDateTime lastSyncedAt;

  @Column(name = "external_account_id", length = 100)
  private String externalAccountId;
}
