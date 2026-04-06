package com.school.supervision.modules.users;

import com.school.supervision.modules.organization.OrganizationRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class DefaultUserInitializer implements ApplicationRunner {
    private static final UUID DEFAULT_ORG_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;

    public DefaultUserInitializer(UserRepository userRepository,
                                  RoleRepository roleRepository,
                                  OrganizationRepository organizationRepository,
                                  PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.organizationRepository = organizationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!organizationRepository.existsById(DEFAULT_ORG_ID)) {
            return;
        }

        Role superAdminRole = ensureRole("SUPER_ADMIN", "System-level super administrator");
        ensureRole("CLUSTER_COORDINATOR", "Cluster coordinator administrator");
        ensureRole("SUPERVISOR", "Field supervision officer");
        ensureRole("TEACHER", "Teacher role");
        ensureRole("SCHOOL_DIRECTOR", "School director role");

        // Only seed the super admin. Coordinators, supervisors, teachers, and directors are created via the app.
        ensureUser(
                "superadmin",
                "Super Admin",
                "superadmin@supervision.local",
                "Admin@12345",
                superAdminRole
        );
    }

    private Role ensureRole(String name, String description) {
        return roleRepository.findByOrganizationIdAndName(DEFAULT_ORG_ID, name).orElseGet(() -> {
            Role role = new Role();
            role.setOrganizationId(DEFAULT_ORG_ID);
            role.setName(name);
            role.setDescription(description);
            role.setSystemRole(true);
            return roleRepository.save(role);
        });
    }

    private void ensureUser(String username, String fullName, String email, String rawPassword, Role role) {
        userRepository.findByUsername(username).orElseGet(() -> {
            User user = new User();
            user.setOrganizationId(DEFAULT_ORG_ID);
            user.setUsername(username);
            user.setFullName(fullName);
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            user.getRoles().add(role);
            return userRepository.save(user);
        });
    }
}
