package com.nexulor.notification.readmodel;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface StatementEntryRepository extends MongoRepository<StatementEntryDocument, String> {

    List<StatementEntryDocument> findByWalletIdOrderByCompletedAtDesc(UUID walletId);
}
