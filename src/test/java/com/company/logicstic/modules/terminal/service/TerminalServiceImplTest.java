package com.company.logicstic.modules.terminal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.company.logicstic.modules.terminal.entity.Terminal;
import com.company.logicstic.modules.terminal.enums.TerminalType;
import com.company.logicstic.modules.terminal.mapper.TerminalMapper;
import com.company.logicstic.modules.terminal.repository.TerminalRepository;
import com.company.logicstic.modules.terminal.service.impl.TerminalServiceImpl;
import com.company.logicstic.modules.terminal.testdata.TerminalTestData;
import com.company.logicstic.shared.exception.ConflictException;
import com.company.logicstic.shared.exception.ResourceNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for {@link TerminalServiceImpl} and the reference for {@code
 * docs/docs/development/engineering-conventions.md} §15: no Spring context, repository mocked with
 * Mockito, the real generated mapper (a mapper is pure code — mocking it would assert nothing),
 * Given–When–Then bodies, AssertJ assertions, one behaviour per test, and the failure paths
 * asserted alongside the happy path.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TerminalServiceImpl")
class TerminalServiceImplTest {

  @Mock private TerminalRepository terminalRepository;

  @Captor private ArgumentCaptor<Terminal> terminalCaptor;

  private final TerminalMapper terminalMapper = Mappers.getMapper(TerminalMapper.class);

  private TerminalServiceImpl service() {
    return new TerminalServiceImpl(terminalRepository, terminalMapper);
  }

  @Test
  void should_normalise_code_and_country_when_creating_terminal() {
    // given
    given(terminalRepository.existsByCodeIgnoreCase("USNYC")).willReturn(false);
    given(terminalRepository.save(any(Terminal.class))).willAnswer(call -> call.getArgument(0));

    // when
    var response = service().create(TerminalTestData.createRequest().withCode("usnyc").build());

    // then
    verify(terminalRepository).save(terminalCaptor.capture());
    assertThat(terminalCaptor.getValue().getCode()).isEqualTo("USNYC");
    assertThat(terminalCaptor.getValue().getCountryCode()).isEqualTo("US");
    assertThat(response.code()).isEqualTo("USNYC");
    assertThat(response.type()).isEqualTo(TerminalType.SEA_PORT);
  }

  @Test
  void should_store_type_using_database_representation_when_creating_terminal() {
    // given
    given(terminalRepository.existsByCodeIgnoreCase("USNYC")).willReturn(false);
    given(terminalRepository.save(any(Terminal.class))).willAnswer(call -> call.getArgument(0));

    // when
    service().create(TerminalTestData.createRequest().withType(TerminalType.RAIL_TERMINAL).build());

    // then — the text column keeps the legacy .NET value, not the enum constant name
    verify(terminalRepository).save(terminalCaptor.capture());
    assertThat(terminalCaptor.getValue().getType()).isEqualTo("RailTerminal");
  }

  @Test
  void should_reject_creation_when_code_already_exists() {
    // given
    given(terminalRepository.existsByCodeIgnoreCase("USNYC")).willReturn(true);

    // when / then
    assertThatThrownBy(() -> service().create(TerminalTestData.createRequest().build()))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("USNYC");

    verify(terminalRepository, never()).save(any());
  }

  @Test
  void should_allow_update_when_code_is_unchanged_on_same_terminal() {
    // given
    UUID id = UUID.randomUUID();
    Terminal existing = TerminalTestData.terminal().withId(id).withCode("USNYC").build();
    given(terminalRepository.findById(id)).willReturn(Optional.of(existing));
    given(terminalRepository.existsByCodeIgnoreCaseAndIdNot("USNYC", id)).willReturn(false);

    // when
    var response =
        service().update(id, TerminalTestData.createRequest().withName("Port of NY/NJ").build());

    // then
    assertThat(response.name()).isEqualTo("Port of NY/NJ");
    // The managed entity is flushed on commit; an explicit save() would be redundant.
    verify(terminalRepository, never()).save(any());
  }

  @Test
  void should_reject_update_when_code_belongs_to_another_terminal() {
    // given
    UUID id = UUID.randomUUID();
    given(terminalRepository.findById(id))
        .willReturn(Optional.of(TerminalTestData.terminal().withId(id).build()));
    given(terminalRepository.existsByCodeIgnoreCaseAndIdNot("NLRTM", id)).willReturn(true);

    // when / then
    assertThatThrownBy(
            () -> service().update(id, TerminalTestData.createRequest().withCode("nlrtm").build()))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("NLRTM");
  }

  @Test
  void should_report_not_found_when_terminal_is_missing() {
    // given
    UUID id = UUID.randomUUID();
    given(terminalRepository.findById(id)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> service().getById(id))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining(id.toString());
  }
}
