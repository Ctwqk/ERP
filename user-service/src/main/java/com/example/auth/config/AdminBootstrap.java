package com.example.auth.config;

import com.example.auth.domain.AppRole;
import com.example.auth.domain.AppRole.RoleName;
import com.example.auth.domain.AppUser;
import com.example.auth.domain.AppUserRole;
import com.example.auth.repository.AppRoleRepository;
import com.example.auth.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first administrator from {@code erp.bootstrap.admin.*} (environment variables ERP_ADMIN_EMAIL and
 * ERP_ADMIN_PASSWORD), so a fresh deployment is usable without a chicken-and-egg problem. Nothing is created unless
 * both values are set, and an existing account is never modified.
 */
@Component
@Order(2) // after RoleSeeder
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppUserRepository users;
    private final AppRoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;
    private final String name;

    public AdminBootstrap(AppUserRepository users, AppRoleRepository roles, PasswordEncoder passwordEncoder,
            @Value("${erp.bootstrap.admin.email:}") String email,
            @Value("${erp.bootstrap.admin.password:}") String password,
            @Value("${erp.bootstrap.admin.name:Administrator}") String name) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
        this.name = name;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            log.info("No bootstrap admin configured (ERP_ADMIN_EMAIL / ERP_ADMIN_PASSWORD); skipping");
            return;
        }
        if (users.findByEmail(email).isPresent()) {
            log.info("Bootstrap admin {} already exists", email);
            return;
        }
        AppUser admin = new AppUser(name, email, passwordEncoder.encode(password));
        for (RoleName roleName : new RoleName[] {RoleName.ADMIN, RoleName.USER}) {
            AppRole role = roles.findByName(roleName).orElseThrow();
            admin.addUserRole(new AppUserRole(admin, role));
        }
        users.save(admin);
        log.info("Created bootstrap admin {}", email);
    }
}
