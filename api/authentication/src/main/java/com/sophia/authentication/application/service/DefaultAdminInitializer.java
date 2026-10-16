package com.sophia.authentication.application.service;

import com.sophia.authentication.domain.config.ApplicationProperties;
import com.sophia.authentication.domain.entity.Role;
import com.sophia.authentication.domain.entity.User;
import com.sophia.authentication.domain.entity.UserPreferences;
import com.sophia.authentication.domain.entity.enums.AuthenticationProvider;
import com.sophia.authentication.infrastructure.repository.RoleRepository;
import com.sophia.authentication.infrastructure.repository.UserPreferencesRepository;
import com.sophia.authentication.infrastructure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultAdminInitializer implements ApplicationRunner {

    private static final String ADMIN_ROLE = "ADMIN";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserPreferencesRepository userPreferencesRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationProperties properties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ApplicationProperties.Admin config = properties.admin();
        if (!config.seedEnabled()) {
            return;
        }
        if (userRepository.existsByEmailIgnoreCase(config.email())) {
            return;
        }

        Role adminRole = roleRepository.findByName(ADMIN_ROLE)
                .orElseThrow(() -> new IllegalStateException(
                        "ADMIN role not found; roles seed migration must run before admin bootstrap"));

        User admin = User.create(
                config.firstName(), config.lastName(), config.email().toLowerCase().trim(),
                passwordEncoder.encode(config.password()), AuthenticationProvider.LOCAL, true, true,
                Set.of(adminRole));
        admin = userRepository.save(admin);
        userPreferencesRepository.save(UserPreferences.create(admin, null));

        log.warn("Seeded default admin account ({}). Change its password immediately in production.",
                config.email());
    }
}
