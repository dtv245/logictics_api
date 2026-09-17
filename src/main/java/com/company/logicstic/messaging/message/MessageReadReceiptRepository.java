package com.company.logicstic.messaging.message;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageReadReceiptRepository extends JpaRepository<MessageReadReceipt, UUID> {}
