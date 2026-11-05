package com.company.logicstic.service;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.document.DocumentView;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.repository.DocumentRepository;
import org.springframework.beans.factory.annotation.Autowired;

@Service
@Transactional(readOnly = true)
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DriverExpiryStatusCalculator expiryCalculator;

    @Autowired
    public DocumentService(DocumentRepository documentRepository, DriverExpiryStatusCalculator expiryCalculator) {
        this.documentRepository = documentRepository;
        this.expiryCalculator = expiryCalculator;
    }

    public DocumentService(DocumentRepository documentRepository) {
        this(documentRepository, new DriverExpiryStatusCalculator(30, "Asia/Ho_Chi_Minh"));
    }

    public PagedResponse<DocumentView> search(String type, String status, UUID loadId, UUID truckId, UUID employeeId,
                                               int page, int pageSize, String orderBy, boolean descending) {
        Sort sort = com.company.logicstic.common.QuerySortPolicy.sort("documents", orderBy, descending);
        var pageable = com.company.logicstic.common.PaginationValidator.pageRequest(page, pageSize, sort);
        return PagedResponse.from(documentRepository.search(type, status, loadId, truckId, employeeId, pageable)
                .map(document -> DocumentView.from(document, expiryCalculator)));
    }

    public DocumentView getById(UUID id) {
        return documentRepository.findById(id)
                .map(document -> DocumentView.from(document, expiryCalculator))
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + id));
    }

    @Transactional
    public void delete(UUID id) {
        if (!documentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Document not found: " + id);
        }
        documentRepository.deleteById(id);
    }
}
