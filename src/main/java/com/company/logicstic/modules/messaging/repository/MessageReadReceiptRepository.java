package com.company.logicstic.modules.messaging.repository;

import com.company.logicstic.modules.messaging.entity.MessageReadReceipt;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageReadReceiptRepository extends JpaRepository<MessageReadReceipt, UUID> {}
