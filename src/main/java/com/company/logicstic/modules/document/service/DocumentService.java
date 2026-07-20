package com.company.logicstic.modules.document.service;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.logicstic.modules.document.dto.DocumentView;
import com.company.logicstic.modules.document.entity.Document;
import com.company.logicstic.modules.document.mapper.DocumentMapper;
import com.company.logicstic.modules.document.repository.DocumentRepository;
import com.company.logicstic.shared.AbstractBaseService;
import com.company.logicstic.shared.dto.PagedResponse;

@Profile("!nodb")
@Service
@Transactional(readOnly = true)
public class DocumentService extends AbstractBaseService<Document, DocumentView, Void> {

    private final DocumentRepository documentRepository;
    private final DocumentMapper documentMapper;

    public DocumentService(DocumentRepository documentRepository, DocumentMapper documentMapper) {
        super(documentRepository, documentMapper::toView, null, null);
        this.documentRepository = documentRepository;
        this.documentMapper = documentMapper;
    }

    @Override
    protected String entityName() {
        return "Document";
    }

    public PagedResponse<DocumentView> search(String type, String status, UUID loadId, UUID truckId, UUID employeeId,
                                               int page, int pageSize, String orderBy, boolean descending) {
        var pageable = pageRequest(page, pageSize, orderBy, descending);
        return toPagedResponse(documentRepository.search(type, status, loadId, truckId, employeeId, pageable));
    }

}