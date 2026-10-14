package com.company.logicstic.exception;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Diagnostic stack locations survive while raw SQL/provider messages do not. */
class ErrorLogTest {
    @Test void nestedSensitiveMessagesAreRemovedFromDiagnosticThrowable() {
        var error=new IllegalStateException("password=private",new java.sql.SQLException("Rejected financial row: private"));
        var safe=ErrorLog.sanitized(error);
        var out=new java.io.StringWriter();safe.printStackTrace(new java.io.PrintWriter(out));
        assertThat(out.toString()).contains("java.lang.IllegalStateException","java.sql.SQLException","ErrorLogTest")
                .doesNotContain("password=private","Rejected financial row");
    }
}
