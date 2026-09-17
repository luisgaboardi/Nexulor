package com.nexulor.wallet.application;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransactionCompletedEventTest {

    @Test
    void carriesEnvelopeVersionAndType() {
        Instant completedAt = Instant.parse("2026-09-28T12:00:00Z");

        TransactionCompletedEvent event = TransactionCompletedEvent.from(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                BigDecimal.ONE,
                "BRL",
                completedAt);

        assertEquals(TransactionCompletedEvent.TYPE, event.eventType());
        assertEquals(TransactionCompletedEvent.VERSION, event.eventVersion());
        assertEquals(completedAt, event.completedAt());
    }
}
