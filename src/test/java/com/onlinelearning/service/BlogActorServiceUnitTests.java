package com.onlinelearning.service;

import com.onlinelearning.entity.Role;
import com.onlinelearning.entity.User;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlogActorServiceUnitTests {
    @Mock UserRepository users;
    @InjectMocks BlogActorService actors;

    @Test void acceptsAnEnabledUnblockedUserWithAnAllowedRole() {
        User manager = user("MANAGER", true, false);
        when(users.findByEmailIgnoreCase(manager.getEmail())).thenReturn(Optional.of(manager));

        assertSame(manager, actors.require(manager.getEmail(), "MANAGER", "ADMIN"));
    }

    @Test void rejectsAnUnknownAccountAsUnauthorized() {
        when(users.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        ApiException error = assertThrows(ApiException.class,
            () -> actors.require("missing@example.com", "STUDENT"));

        assertEquals(401, error.status().value());
    }

    @Test void rejectsDisabledOrBlockedAccountsBeforeCheckingRoles() {
        for (User unavailable : new User[]{user("MANAGER", false, false), user("MANAGER", true, true)}) {
            when(users.findByEmailIgnoreCase(unavailable.getEmail())).thenReturn(Optional.of(unavailable));
            ApiException error = assertThrows(ApiException.class,
                () -> actors.require(unavailable.getEmail(), "MANAGER"));
            assertEquals(403, error.status().value());
        }
    }

    @Test void rejectsAUserOutsideTheAllowedRoleSet() {
        User expert = user("EXPERT", true, false);
        when(users.findByEmailIgnoreCase(expert.getEmail())).thenReturn(Optional.of(expert));

        ApiException error = assertThrows(ApiException.class,
            () -> actors.require(expert.getEmail(), "MANAGER", "ADMIN"));

        assertEquals(403, error.status().value());
    }

    private User user(String roleCode, boolean enabled, boolean blocked) {
        Role role = new Role(); role.setCode(roleCode);
        User user = new User(); user.setEmail(roleCode.toLowerCase() + (enabled ? "-enabled" : "-disabled") + (blocked ? "-blocked" : "") + "@example.com");
        user.setEnabled(enabled); user.setBlocked(blocked); user.getRoles().add(role);
        return user;
    }
}

