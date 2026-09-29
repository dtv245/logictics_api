package com.company.logicstic.mapper;

import com.company.logicstic.config.MapperConfiguration;
import com.company.logicstic.dto.payment.CreatePaymentRequest;
import com.company.logicstic.dto.payment.PaymentView;
import com.company.logicstic.entity.Payment;
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

    @Mapping(target = "invoiceId", expression = "java(payment.getInvoice() != null ? payment.getInvoice().getId() : null)")
    @Mapping(target = "invoiceNumber", expression = "java(payment.getInvoice() != null ? payment.getInvoice().getNumber() : null)")
    PaymentView toView(Payment payment);
}