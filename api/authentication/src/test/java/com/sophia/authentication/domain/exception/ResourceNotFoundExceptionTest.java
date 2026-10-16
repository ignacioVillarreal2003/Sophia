package com.sophia.authentication.domain.exception;

import com.sophia.authentication.domain.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceNotFoundExceptionTest {

    @Test
    @DisplayName("carries a 404 status and the given message")
    void carriesNotFoundStatus() {
        ResourceNotFoundException exception = new ResourceNotFoundException("No Google account is linked");

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo("No Google account is linked");
    }

    @Test
    @DisplayName("the resource/id constructor formats a consistent message")
    void formatsResourceAndId() {
        UUID id = UUID.randomUUID();

        ResourceNotFoundException exception = new ResourceNotFoundException("User", id);

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo("User not found: " + id);
    }

    @Test
    @DisplayName("the resource/id constructor exposes them as structured details")
    void exposesResourceAndIdAsDetails() {
        UUID id = UUID.randomUUID();

        ResourceNotFoundException exception = new ResourceNotFoundException("User", id);

        assertThat(exception.getDetails())
                .containsEntry("resource", "User")
                .containsEntry("id", id.toString());
    }

    @Test
    @DisplayName("carries the cause when one is given")
    void carriesCause() {
        IllegalStateException cause = new IllegalStateException("boom");

        ApiException exception = new ApiException(
                ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "gone", java.util.Map.of(), cause);

        assertThat(exception.getCause()).isSameAs(cause);
    }
}
