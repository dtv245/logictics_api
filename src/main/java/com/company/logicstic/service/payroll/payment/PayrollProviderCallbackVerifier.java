package com.company.logicstic.service.payroll.payment;
import java.util.Map;
/** Real provider adapters authenticate the original body/headers and return only verified financial identity.
 * No unverified body, admin role or claimed status can substitute for provider authentication. */
public interface PayrollProviderCallbackVerifier {
 String key();
 Verified verify(String originalBody,Map<String,String> headers);
 /** When tenancy is enabled, tenantId must be resolved from authenticated merchant/provider registry,
  * never an unsigned body/header or an assumed default tenant. */
 record Verified(VerifiedPayrollPaymentEvent event,Map<String,Object> verificationProof,String tenantId) {
  public Verified(VerifiedPayrollPaymentEvent event,Map<String,Object> proof) {this(event,proof,null);}
 }
}
