package com.sophia.notification.infrastructure.messaging;

import com.sophia.notification.api.filter.CorrelationIdFilter;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MessageCorrelationTest {

    @AfterEach
    void clearContext() {
        MDC.clear();
    }

    @Test
    void theCanonicalHeaderIsPickedUp() {
        assertThat(MessageCorrelation.from(recordWith("X-Request-Id", "req-123"))).isEqualTo("req-123");
    }

    @Test
    void theLowercaseAndLegacySpellingsAreAccepted() {
        assertThat(MessageCorrelation.from(recordWith("x-request-id", "req-lower"))).isEqualTo("req-lower");
        assertThat(MessageCorrelation.from(recordWith("correlationId", "req-legacy"))).isEqualTo("req-legacy");
    }

    @Test
    void aRecordWithoutHeadersCarriesNoCorrelation() {
        assertThat(MessageCorrelation.from(new ConsumerRecord<>("t", 0, 0L, "k", "v"))).isNull();
    }

    @Test
    void aHeaderThatCouldPoisonTheLogsIsIgnored() {
        assertThat(MessageCorrelation.from(recordWith("X-Request-Id", "bad value with spaces"))).isNull();
        assertThat(MessageCorrelation.from(recordWith("X-Request-Id", "x".repeat(65)))).isNull();
        assertThat(MessageCorrelation.from(recordWith("X-Request-Id", "line\nbreak"))).isNull();
    }

    @Test
    void theCorrelationTravelsThroughTheDiagnosticContext() {
        MessageCorrelation.put("req-123");

        assertThat(MDC.get(CorrelationIdFilter.REQUEST_ID_MDC_KEY)).isEqualTo("req-123");
        assertThat(MessageCorrelation.current()).isEqualTo("req-123");

        MessageCorrelation.clear();
        assertThat(MessageCorrelation.current()).isNull();
    }

    @Test
    void aMissingCorrelationDoesNotOverwriteTheContext() {
        MessageCorrelation.put("req-123");
        MessageCorrelation.put(null);

        assertThat(MessageCorrelation.current()).isEqualTo("req-123");
    }

    private ConsumerRecord<String, String> recordWith(String header, String value) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("t", 0, 0L, "k", "v");
        record.headers().add(header, value.getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
