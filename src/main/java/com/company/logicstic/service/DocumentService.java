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

@Service
@Transactional(readOnly = true)
public class DocumentService {

    private final DocumentRepository documentRepository;

    public DocumentService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public PagedResponse<DocumentView> search(String type, String status, UUID loadId, UUID truckId, UUID employeeId,
                                               int page, int pageSize, String orderBy, boolean descending) {
        Sort sort = descending ? Sort.by(orderBy).descending() : Sort.by(orderBy).ascending();
        var pageable = PageRequest.of(page - 1, pageSize, sort);
        return PagedResponse.from(documentRepository.search(type, status, loadId, truckId, employeeId, pageable)
                .map(DocumentView::from));
    }

    public DocumentView getById(UUID id) {
        return documentRepository.findById(id)
                .map(DocumentView::from)
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