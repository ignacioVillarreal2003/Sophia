package com.sophia.notification.domain.dto.request;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationSearchRequestTest {

    @Test
    void blankFiltersAreTreatedAsAbsent() {
        NotificationSearchRequest request = new NotificationSearchRequest(
                false, "  ", "", "   ", "", null, null, "  ");

        assertThat(request.category()).isNull();
        assertThat(request.type()).isNull();
        assertThat(request.severity()).isNull();
        assertThat(request.groupKey()).isNull();
        assertThat(request.search()).isNull();
        assertThat(request.searchPattern()).isNull();
        assertThat(request.normalizedSeverity()).isNull();
    }

    @Test
    void filtersAreTrimmed() {
        NotificationSearchRequest request = new NotificationSearchRequest(
                false, " SYSTEM ", " MENTION ", null, " thread-1 ", null, null, null);

        assertThat(request.category()).isEqualTo("SYSTEM");
        assertThat(request.type()).isEqualTo("MENTION");
        assertThat(request.groupKey()).isEqualTo("thread-1");
    }

    @Test
    void theSearchTermBecomesACaseInsensitivePattern() {
        NotificationSearchRequest request = new NotificationSearchRequest(
                false, null, null, null, null, null, null, " You Were Mentioned ");

        assertThat(request.searchPattern()).isEqualTo("%you were mentioned%");
    }

    @Test
    void anAbsentUnreadOnlyFlagDefaultsToTheWholeInbox() {
        NotificationSearchRequest request = new NotificationSearchRequest(
                null, null, null, null, null, null, null, null);

        assertThat(request.unreadOnly()).isFalse();
    }

    @Test
    void severityIsMatchedRegardlessOfCase() {
        NotificationSearchRequest request = new NotificationSearchRequest(
                false, null, null, "warning", null, null, null, null);

        assertThat(request.normalizedSeverity()).isEqualTo("WARNING");
    }
}
