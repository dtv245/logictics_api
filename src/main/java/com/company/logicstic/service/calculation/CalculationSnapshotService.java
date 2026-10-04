package com.company.logicstic.service.calculation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import com.company.logicstic.entity.CalculationSnapshot;
import com.company.logicstic.repository.CalculationSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CalculationSnapshotService {

    private final CalculationSnapshotRepository snapshotRepository;

    @Transactional
    public CalculationSnapshot recordSnapshot(
            String entityType,
            UUID entityId,
            String calculationType,
            String engineName,
            String engineVersion,
            String policyType,
            UUID policyId,
            String policyVersion,
            String inputJson,
            String resultJson,
            String currency,
            UUID calculatedBy,
            String correlationId
    ) {
        CalculationSnapshot snapshot = new CalculationSnapshot();
        snapshot.setEntityType(entityType);
        snapshot.setEntityId(entityId);
        snapshot.setCalculationType(calculationType);
        snapshot.setEngineName(engineName);
        snapshot.setEngineVersion(engineVersion);
        snapshot.setPolicyType(policyType);
        snapshot.setPolicyId(policyId);
        snapshot.setPolicyVersion(policyVersion);
        snapshot.setInputJson(inputJson != null ? inputJson : "{}");
        snapshot.setResultJson(resultJson != null ? resultJson : "{}");
        snapshot.setCurrency(currency);
        snapshot.setCalculatedBy(calculatedBy);
        snapshot.setCorrelationId(correlationId);
        snapshot.setCalculatedAt(OffsetDateTime.now());
        snapshot.setChecksum(computeChecksum(snapshot.getInputJson(), snapshot.getResultJson()));

        return snapshotRepository.save(snapshot);
    }

    @Transactional(readOnly = true)
    public List<CalculationSnapshot> getSnapshotsForEntity(String entityType, UUID entityId) {
        return snapshotRepository.findByEntityTypeAndEntityIdOrderByCalculatedAtDesc(entityType, entityId);
    }

    private String computeChecksum(String input, String result) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((input + "::" + result).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required for financial snapshot integrity", e);
        }
    }
}
