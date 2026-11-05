package com.company.logicstic.mapper;

import com.company.logicstic.dto.driverprofile.DriverBankAccountSensitiveView;
import com.company.logicstic.dto.driverprofile.DriverBankAccountView;
import com.company.logicstic.entity.DriverBankAccount;
import com.company.logicstic.service.DriverPiiCipher;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DriverBankAccountMapper {
    private static final String ACCOUNT_CONTEXT = "driver-bank-account-number";
    private final DriverPiiCipher piiCipher;

    public DriverBankAccountMapper(DriverPiiCipher piiCipher) {
        this.piiCipher = piiCipher;
    }

    public DriverBankAccountView toView(DriverBankAccount account) {
        UUID employeeId = account.getEmployee().getId();
        return new DriverBankAccountView(account.getId(), employeeId, account.getBankName(),
                mask(clear(account.getAccountNumber(), employeeId)), account.getAccountHolderName(),
                account.isPrimary(), account.isActive(), account.getCreatedAt(), account.getLastModifiedAt());
    }

    public DriverBankAccountSensitiveView toSensitiveView(DriverBankAccount account) {
        UUID employeeId = account.getEmployee().getId();
        return new DriverBankAccountSensitiveView(account.getId(), employeeId, account.getBankName(),
                clear(account.getAccountNumber(), employeeId), account.getAccountHolderName(),
                account.isPrimary(), account.isActive(), account.getCreatedAt(), account.getLastModifiedAt());
    }

    private String clear(String encrypted, UUID employeeId) {
        return piiCipher.decryptSensitiveValue(encrypted, employeeId, ACCOUNT_CONTEXT);
    }

    private static String mask(String value) {
        if (value == null || value.isBlank()) return null;
        return value.length() <= 4 ? "****" : "****" + value.substring(value.length() - 4);
    }
}
