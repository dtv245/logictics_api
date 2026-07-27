package com.company.logicstic.modules.finance.mapper;

import com.company.logicstic.modules.finance.dto.request.CreatePaymentRequest;
import com.company.logicstic.modules.finance.dto.response.PaymentResponse;
import com.company.logicstic.modules.finance.entity.Payment;
import com.company.logicstic.shared.config.MapperConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = MapperConfiguration.class)
public interface PaymentMapper {

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "invoice", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "lastModifiedAt", ignore = true)
  @Mapping(target = "lastModifiedBy", ignore = true)
  Payment toEntity(CreatePaymentRequest req);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "invoice", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "lastModifiedAt", ignore = true)
  @Mapping(target = "lastModifiedBy", ignore = true)
  void updateEntity(CreatePaymentRequest req, @MappingTarget Payment payment);

  @Mapping(
      target = "invoiceId",
      expression = "java(payment.getInvoice() != null ? payment.getInvoice().getId() : null)")
  @Mapping(
      target = "invoiceNumber",
      expression = "java(payment.getInvoice() != null ? payment.getInvoice().getNumber() : null)")
  PaymentResponse toResponse(Payment payment);
}
