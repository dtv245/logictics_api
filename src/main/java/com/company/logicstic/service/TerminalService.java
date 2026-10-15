package com.company.logicstic.service;

import com.company.logicstic.common.PaginationValidator;
import com.company.logicstic.common.QuerySortPolicy;
import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.terminal.CreateTerminalRequest;
import com.company.logicstic.dto.terminal.TerminalView;
import com.company.logicstic.entity.Terminal;
import com.company.logicstic.exception.ConflictException;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.repository.TerminalRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TerminalService {

    private final TerminalRepository terminalRepository;

    public TerminalService(TerminalRepository terminalRepository) {
        this.terminalRepository = terminalRepository;
    }

    public PagedResponse<TerminalView> search(String search, String type, String countryCode,
                                              int page, int pageSize, String orderBy, boolean descending) {
        Sort sort = QuerySortPolicy.sort("terminals", orderBy, descending);
        Pageable pageable = PaginationValidator.pageRequest(page, pageSize, sort);
        return PagedResponse.from(terminalRepository.search(search, type, countryCode, pageable).map(TerminalView::from));
    }

    public TerminalView getById(UUID id) {
        return terminalRepository.findById(id)
                .map(TerminalView::from)
                .orElseThrow(() -> new ResourceNotFoundException("Terminal not found: " + id));
    }

    @Transactional
    public TerminalView create(CreateTerminalRequest request) {
        String code = request.code().trim().toUpperCase();
        if (terminalRepository.existsByCodeIgnoreCase(code)) {
            throw ConflictException.forUniqueField("code", "Terminal with code '" + code + "' already exists");
        }
        Terminal terminal = new Terminal();
        applyFields(terminal, request);
        terminal.setCode(code);
        return TerminalView.from(terminalRepository.save(terminal));
    }

    @Transactional
    public TerminalView update(UUID id, CreateTerminalRequest request) {
        Terminal terminal = terminalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Terminal not found: " + id));
        String code = request.code().trim().toUpperCase();
        if (terminalRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw ConflictException.forUniqueField("code", "Terminal with code '" + code + "' already exists");
        }
        applyFields(terminal, request);
        terminal.setCode(code);
        return TerminalView.from(terminalRepository.save(terminal));
    }

    @Transactional
    public void delete(UUID id) {
        if (!terminalRepository.existsById(id)) {
            throw new ResourceNotFoundException("Terminal not found: " + id);
        }
        terminalRepository.deleteById(id);
    }

    private void applyFields(Terminal terminal, CreateTerminalRequest req) {
        terminal.setName(req.name());
        terminal.setCountryCode(req.countryCode() != null ? req.countryCode().trim().toUpperCase() : null);
        terminal.setType(req.type());
        terminal.setNotes(req.notes());
        terminal.setAddressCity(req.addressCity());
        terminal.setAddressCountry(req.addressCountry());
        terminal.setAddressLine1(req.addressLine1());
        terminal.setAddressLine2(req.addressLine2());
        terminal.setAddressState(req.addressState());
        terminal.setAddressZipCode(req.addressZipCode());
    }
}
