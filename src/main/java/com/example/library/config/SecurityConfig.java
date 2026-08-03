package com.example.library.config;

import com.example.library.repository.AppUserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/books/*/history").hasRole("OWNER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/books/*/enrich").hasRole("OWNER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/books/*/borrow").hasRole("CLIENT")
                        .requestMatchers(HttpMethod.POST, "/api/v1/books/*/waitlist").hasRole("CLIENT")
                        .requestMatchers(HttpMethod.GET, "/api/v1/books", "/api/v1/books/*")
                            .hasAnyRole("CLIENT", "OWNER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/books").hasRole("OWNER")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/books/*").hasRole("OWNER")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/books/*").hasRole("OWNER")
                        .requestMatchers("/api/v1/loans/**", "/api/v1/waitlist/**", "/api/v1/late-fees/**")
                            .hasRole("CLIENT")
                        .requestMatchers("/api/v1/recommendations/**").hasRole("CLIENT")
                        .requestMatchers("/api/v1/ai/**").hasAnyRole("CLIENT", "OWNER")
                        .requestMatchers("/mcp", "/mcp/**").hasAnyRole("CLIENT", "OWNER")
                        .anyRequest().authenticated());
        return http.build();
    }

    @Bean
    UserDetailsService userDetailsService(AppUserRepository repository) {
        return username -> repository.findByUsername(username)
                .map(user -> User.withUsername(user.getUsername())
                        .password(user.getPasswordHash())
                        .roles(user.getRole().name())
                        .disabled(!user.isEnabled())
                        .build())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
