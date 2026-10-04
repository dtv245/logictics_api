package com.company.logicstic.integration.lark.base;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.lark.base")
public record LarkBaseProperties(
    boolean enabled,
    String appToken,
    String loadsTableId,
    String driversTableId) {

  public LarkBaseProperties {
    if (appToken == null) {
      appToken = "";
    }
    if (loadsTableId == null) {
      loadsTableId = "";
    }
    if (driversTableId == null) {
      driversTableId = "";
    }
  }
}
