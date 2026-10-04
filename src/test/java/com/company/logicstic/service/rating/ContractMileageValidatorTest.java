package com.company.logicstic.service.rating;

import com.company.logicstic.dto.rating.ContractMileageRequest;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.service.rating.domain.RatingMileageComponent;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ContractMileageValidatorTest {
    private ContractMileageRequest request(String value, String unit, String source) {
        return new ContractMileageRequest(RatingMileageComponent.LINEHAUL, UUID.randomUUID(), 1, new BigDecimal(value), unit, source);
    }
    @Test void zeroIsAvailableMileageNotAnUnavailableOrFakeCharge() {
        assertDoesNotThrow(() -> ContractMileageValidator.validate(request("0", "MILE", "Agreement reference")));
    }
    @Test void originalDecimalAndTrailingZerosArePreservedWithoutRounding() {
        var r = request("12.3000", "MILE", "Agreement reference"); ContractMileageValidator.validate(r);
        assertEquals("12.3000", r.originalValue().toPlainString());
    }
    @Test void negativeExcessPrecisionAndOverflowRejectRatherThanRound() {
        for (var value : new String[] {"-1", "1.0001", "1000000000"})
            assertThrows(BadRequestException.class, () -> ContractMileageValidator.validate(request(value, "MILE", "Agreement reference")));
    }
    @Test void bareMilesUnsupportedUnitAndMonetaryMinimumCannotBecomeContractEvidence() {
        assertThrows(BadRequestException.class, () -> ContractMileageValidator.validate(request("1", "MILE", "")));
        assertThrows(BadRequestException.class, () -> ContractMileageValidator.validate(request("1", "KM", "Agreement reference")));
        assertThrows(BadRequestException.class, () -> ContractMileageValidator.validate(new ContractMileageRequest(
                RatingMileageComponent.MINIMUM_CHARGE, UUID.randomUUID(), 1, BigDecimal.ONE, "MILE", "Agreement reference")));
    }
}
