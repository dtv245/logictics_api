package com.company.logicstic.dto.payment;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;

/** Metadata only. Unknown legacy financial fields are rejected by the command. */
public class UpdatePaymentRequest {
    private String description;
    private String referenceNumber;
    private boolean descriptionSupplied;
    private boolean referenceSupplied;
    private boolean unsupportedFields;
    public String getDescription() { return description; }
    public void setDescription(String value) { description=value; descriptionSupplied=true; }
    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String value) { referenceNumber=value; referenceSupplied=true; }
    @JsonAnySetter public void unsupportedField(String name,Object value) { unsupportedFields=true; }
    @JsonIgnore @Schema(hidden=true) public boolean hasDescription() { return descriptionSupplied; }
    @JsonIgnore @Schema(hidden=true) public boolean hasReferenceNumber() { return referenceSupplied; }
    @JsonIgnore @Schema(hidden=true) public boolean hasUnsupportedFields() { return unsupportedFields; }
}
