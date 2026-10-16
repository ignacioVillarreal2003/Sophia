package com.sophia.authentication.domain.dto.request;

import com.sophia.authentication.domain.entity.enums.Theme;
import com.sophia.authentication.domain.entity.enums.ThemeMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Partial user update (PATCH) — only non-null fields are applied")
public record PatchUserRequest(
        @Size(max = 100)
        String firstName,

        @Size(max = 100)
        String lastName,

        @Email(message = "Email must be a valid address")
        @Size(max = 255)
        String email,

        Boolean active,

        Theme theme,

        ThemeMode themeMode,

        @Size(max = 10)
        String locale,

        @Size(max = 50)
        String timezone,

        @Schema(description = "Module slugs to hide from the home screen. Absent leaves the "
                + "current selection untouched; an empty array clears it (everything shows).")
        @Size(max = 50, message = "At most 50 hidden modules")
        List<@NotBlank @Size(max = 64) @Pattern(
                regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
                message = "Module slug must be lowercase alphanumeric segments separated by hyphens"
        ) String> hiddenModules) {
}
