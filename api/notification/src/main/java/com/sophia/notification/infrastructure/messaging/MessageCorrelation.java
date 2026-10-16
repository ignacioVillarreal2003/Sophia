package com.sophia.notification.infrastructure.messaging;

import com.sophia.notification.api.filter.CorrelationIdFilter;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

public final class MessageCorrelation {

    public static final String HEADER = "X-Request-Id";

    private static final List<String> CANDIDATE_HEADERS = List.of(HEADER, "x-request-id", "correlationId");
    private static final Pattern ACCEPTABLE = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    private MessageCorrelation() {
    }

    public static String from(ConsumerRecord<?, ?> record) {
        for (String name : CANDIDATE_HEADERS) {
            Header header = record.headers().lastHeader(name);
            if (header == null || header.value() == null) {
                continue;
            }
            String value = new String(header.value(), StandardCharsets.UTF_8);
            if (ACCEPTABLE.matcher(value).matches()) {
                return value;
            }
        }
        return null;
    }

    public static void put(String correlationId) {
        if (correlationId != null) {
            MDC.put(CorrelationIdFilter.REQUEST_ID_MDC_KEY, correlationId);
        }
    }

    public static String current() {
        return MDC.get(CorrelationIdFilter.REQUEST_ID_MDC_KEY);
    }

    public static void clear() {
        MDC.remove(CorrelationIdFilter.REQUEST_ID_MDC_KEY);
    }
}
