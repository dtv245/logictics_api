package com.company.logicstic.service.rating;

import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.dto.rating.*;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.service.rating.domain.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component @RequiredArgsConstructor
public class RatingFingerprintService {
    private final ObjectMapper json;
    public String hash(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical(json.valueToTree(value)).getBytes(StandardCharsets.UTF_8)));
        } catch(java.security.NoSuchAlgorithmException ex){throw new AssertionError("Required SHA-256 unavailable",ex);}
    }
    private String canonical(JsonNode n){
        if(n.isNumber())return n.decimalValue().stripTrailingZeros().toPlainString();
        if(n.isArray()){List<String> values=new ArrayList<>();n.forEach(v->values.add(canonical(v)));return "["+String.join(",",values)+"]";}
        if(n.isObject()){
            List<String> fields=new ArrayList<>();
            n.properties().stream().sorted(Map.Entry.comparingByKey()).forEach(e->fields.add(json.writeValueAsString(e.getKey())+":"+canonical(e.getValue())));
            return "{"+String.join(",",fields)+"}";
        }
        return json.writeValueAsString(n);
    }
    public String inputs(RatingInputs inputs){return hash(inputs);}
    public String result(RatingPreview preview){
        ObjectNode n=(ObjectNode)json.valueToTree(preview);
        n.remove("calculatedAt");n.remove("correlationId");n.remove("inputHash");n.remove("resultHash");
        // A retrieval instant is audit, not a changed commercial quote. Value/version/hash remain material.
        var index=n.path("fuelSurcharge").path("index");if(index instanceof ObjectNode o)o.remove("retrievedAt");
        return hash(n);
    }
    public RatingPreview stamp(RatingPreview p){
        return new RatingPreview(p.inputs(),p.rawLinehaul(),p.boundedLinehaul(),p.fuelSurcharge(),p.lines(),p.subtotal(),p.currency(),p.currencyScale(),
                p.taxAvailability(),p.roundingPolicyCode(),p.roundingPolicyVersion(),p.calculatedAt(),p.correlationId(),inputs(p.inputs()),result(p));
    }
    public String command(UUID load,RatingAcceptRequest request){
        var r=request.rating();if(r==null||r.accessorialIds()==null||r.accessorialIds().stream().anyMatch(Objects::isNull))
            throw new BadRequestException("RATING_VALIDATION_REQUIRED","Explicit rating request/accessorial selection required");
        var normalized=new RatingPreviewRequest(r.contractId(),r.contractVersion(),r.lane(),r.equipment(),r.service(),r.tier(),
                CurrencyGuard.canonical(r.currency()),r.contextSource(),r.linehaulMileageEvidenceId(),r.fscMileageEvidenceId(),
                r.accessorialIds().stream().sorted(Comparator.comparing(UUID::toString)).toList());
        Map<String,Object> fields=new TreeMap<>();fields.put("loadId",load);fields.put("rating",normalized);
        fields.put("expectedInputHash",request.expectedInputHash());fields.put("expectedResultHash",request.expectedResultHash());
        fields.put("supersedesSnapshotId",request.supersedesSnapshotId());fields.put("reasonCode",request.reasonCode());fields.put("reason",request.reason());
        return hash(fields);
    }
}
