package com.onlinelearning.config;

import com.onlinelearning.entity.Role;
import com.onlinelearning.entity.RoleName;
import com.onlinelearning.entity.User;
import com.onlinelearning.repository.RoleRepository;
import com.onlinelearning.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

@Configuration
public class RoleDataInitializer {

    @Bean
    CommandLineRunner rolesAndDemoUsers(
            RoleRepository roles,
            UserRepository users,
            PasswordEncoder passwordEncoder,
            @Value("${app.seed-demo-users.enabled:true}") boolean seedDemoUsers,
            @Value("${app.seed-demo-users.password:Online@123}") String demoPassword) {
        return args -> {
            for (RoleName roleName : RoleName.values()) {
                String code = roleName.name().replace("ROLE_", "");
                if (roles.findByCode(code).isEmpty()) {
                    Role role = new Role();
                    role.setCode(code);
                    role.setName(code);
                    role.setDescription(code);
                    roles.save(role);
                }
            }

            if (!seedDemoUsers) {
                return;
            }

            createDemoUser(users, roles, passwordEncoder, demoPassword,
                    "admin@onlinelearning.local", "Online Learning Admin", "ADMIN");
            createDemoUser(users, roles, passwordEncoder, demoPassword,
                    "manager@onlinelearning.local", "Online Learning Manager", "MANAGER");
            createDemoUser(users, roles, passwordEncoder, demoPassword,
                    "expert@onlinelearning.local", "Online Learning Expert", "EXPERT");
            createDemoUser(users, roles, passwordEncoder, demoPassword,
                    "student@onlinelearning.local", "Online Learning Student", "STUDENT");
        };
    }

    private void createDemoUser(
            UserRepository users,
            RoleRepository roles,
            PasswordEncoder passwordEncoder,
            String password,
            String email,
            String fullName,
            String roleCode) {
        if (users.existsByEmailIgnoreCase(email)) {
            return;
        }

        Role role = roles.findByCode(roleCode)
                .orElseThrow(() -> new IllegalStateException("Role was not initialized: " + roleCode));
        User user = new User();
        user.setEmail(email);
        user.setFullName(fullName);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEnabled(true);
        user.setBlocked(false);
        user.setRoles(Set.of(role));
        users.save(user);
    }
}
