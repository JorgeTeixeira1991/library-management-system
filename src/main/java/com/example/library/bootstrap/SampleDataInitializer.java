package com.example.library.bootstrap;

import com.example.library.domain.AppUser;
import com.example.library.domain.Role;
import com.example.library.repository.AppUserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SampleDataInitializer {

    @Bean
    CommandLineRunner seedUsers(AppUserRepository users, PasswordEncoder encoder) {
        return args -> {
            createIfMissing(users, encoder, "owner", "owner123", Role.OWNER);
            createIfMissing(users, encoder, "client", "client123", Role.CLIENT);
            createIfMissing(users, encoder, "client2", "client2123", Role.CLIENT);
        };
    }

    private void createIfMissing(
            AppUserRepository users,
            PasswordEncoder encoder,
            String username,
            String password,
            Role role) {
        if (users.findByUsername(username).isPresent()) {
            return;
        }
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(encoder.encode(password));
        user.setRole(role);
        user.setEnabled(true);
        users.save(user);
    }
}
