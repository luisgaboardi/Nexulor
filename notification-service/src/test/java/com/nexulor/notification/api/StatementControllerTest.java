package com.nexulor.notification.api;

import com.nexulor.notification.readmodel.StatementEntryDocument;
import com.nexulor.notification.readmodel.StatementEntryRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatementControllerTest {

    @Test
    void mapsDocumentsToResponses() {
        StatementEntryRepository repository = mock(StatementEntryRepository.class);
        UUID walletId = UUID.randomUUID();
        StatementEntryDocument doc = new StatementEntryDocument();
        doc.setId("evt:DEBIT");
        doc.setWalletId(walletId);
        doc.setTransferId(UUID.randomUUID());
        doc.setCounterpartyWalletId(UUID.randomUUID());
        doc.setDirection("DEBIT");
        doc.setAmount(new BigDecimal("10.00"));
        doc.setCurrency("BRL");
        doc.setCompletedAt(Instant.parse("2026-09-28T12:00:00Z"));
        when(repository.findByWalletIdOrderByCompletedAtDesc(walletId)).thenReturn(List.of(doc));

        StatementController controller = new StatementController(repository);
        List<StatementController.StatementResponse> response = controller.statement(walletId);

        assertEquals(1, response.size());
        assertEquals("DEBIT", response.get(0).direction());
        assertEquals("evt:DEBIT", response.get(0).id());
    }
}
