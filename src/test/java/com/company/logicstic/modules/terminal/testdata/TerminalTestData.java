package com.company.logicstic.modules.terminal.testdata;

import com.company.logicstic.modules.terminal.dto.request.CreateTerminalRequest;
import com.company.logicstic.modules.terminal.entity.Terminal;
import com.company.logicstic.modules.terminal.enums.TerminalType;
import java.util.UUID;

/**
 * Object mother for the terminal aggregate.
 *
 * <p>Tests build fixtures from here instead of copy-pasting a setup block: when a required column
 * is added, one file changes instead of every test. Defaults are always valid, and each test
 * overrides only the field it is about, which makes the intent of the test obvious.
 */
public final class TerminalTestData {

  private TerminalTestData() {}

  public static TerminalBuilder terminal() {
    return new TerminalBuilder();
  }

  public static RequestBuilder createRequest() {
    return new RequestBuilder();
  }

  /** Builds a persisted-looking entity. */
  public static final class TerminalBuilder {
    private UUID id = UUID.randomUUID();
    private String name = "Port of New York and New Jersey";
    private String code = "USNYC";
    private String countryCode = "US";
    private TerminalType type = TerminalType.SEA_PORT;

    public TerminalBuilder withId(UUID value) {
      this.id = value;
      return this;
    }

    public TerminalBuilder withName(String value) {
      this.name = value;
      return this;
    }

    public TerminalBuilder withCode(String value) {
      this.code = value;
      return this;
    }

    public TerminalBuilder withType(TerminalType value) {
      this.type = value;
      return this;
    }

    public Terminal build() {
      Terminal terminal = new Terminal();
      terminal.setId(id);
      terminal.setName(name);
      terminal.setCode(code);
      terminal.setCountryCode(countryCode);
      terminal.setTypeEnum(type);
      terminal.setAddressLine1("1 Terminal Way");
      terminal.setAddressCity("Newark");
      terminal.setAddressState("NJ");
      terminal.setAddressZipCode("07114");
      terminal.setAddressCountry("US");
      return terminal;
    }
  }

  /** Builds a create/update request. Defaults use lower-case input to exercise normalisation. */
  public static final class RequestBuilder {
    private String name = "Port of New York and New Jersey";
    private String code = "USNYC";
    private String countryCode = "us";
    private TerminalType type = TerminalType.SEA_PORT;

    public RequestBuilder withName(String value) {
      this.name = value;
      return this;
    }

    public RequestBuilder withCode(String value) {
      this.code = value;
      return this;
    }

    public RequestBuilder withType(TerminalType value) {
      this.type = value;
      return this;
    }

    public CreateTerminalRequest build() {
      return new CreateTerminalRequest(
          name,
          code,
          countryCode,
          type,
          null,
          "1 Terminal Way",
          null,
          "Newark",
          "NJ",
          "07114",
          "US");
    }
  }
}
