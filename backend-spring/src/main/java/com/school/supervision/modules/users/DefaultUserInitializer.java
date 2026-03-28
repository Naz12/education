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
        Role coordinatorRole = ensureRole("CLUSTER_COORDINATOR", "Cluster coordinator administrator");
        Role supervisorRole = ensureRole("SUPERVISOR", "Field supervision officer");
        Role teacherRole = ensureRole("TEACHER", "Teacher role");
        Role schoolDirectorRole = ensureRole("SCHOOL_DIRECTOR", "School director role");

        ensureUser(
                "superadmin",
                "Super Admin",
                "superadmin@supervision.local",
                "Admin@12345",
                superAdminRole
        );
        ensureUser(
                "clustercoordinator",
                "Cluster Coordinator",
                "coordinator@supervision.local",
                "Coordinator@12345",
                coordinatorRole
        );
        ensureUser(
                "supervisor1",
                "Default Supervisor",
                "supervisor1@supervision.local",
                "Supervisor@12345",
                supervisorRole
        );
        ensureUser(
                "teacher1",
                "Default Teacher",
                "teacher1@supervision.local",
                "Teacher@12345",
                teacherRole
        );
        ensureUser(
                "director1",
                "Default School Director",
                "director1@supervision.local",
                "Director@12345",
                schoolDirectorRole
        );

        userRepository.findByUsernameAndOrganizationId("clustercoordinator", DEFAULT_ORG_ID).ifPresent(u -> {
            if (u.getCity() == null) {
                u.setCity("Addis Ababa");
                u.setSubCity("Bole");
                u.setWereda("W01");
                userRepository.save(u);
            }
        });
        userRepository.findByUsernameAndOrganizationId("supervisor1", DEFAULT_ORG_ID).ifPresent(u -> {
            userRepository.findByUsernameAndOrganizationId("clustercoordinator", DEFAULT_ORG_ID).ifPresent(c -> {
                boolean dirty = false;
                if (u.getCoordinatorUserId() == null) {
                    u.setCoordinatorUserId(c.getId());
                    dirty = true;
                }
                if (u.getCity() == null && c.getCity() != null) {
                    u.setCity(c.getCity());
                    u.setSubCity(c.getSubCity());
                    u.setWereda(c.getWereda());
                    dirty = true;
                }
                if (dirty) {
                    userRepository.save(u);
                }
            });
        });
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
