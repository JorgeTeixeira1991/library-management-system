package com.example.library.bootstrap.remote;

import java.net.URI;
import java.time.Duration;
import org.springframework.core.env.Environment;

record RemoteDatabaseBootstrapProperties(
        URI vaultUri,
        String oidcRole,
        String databaseRole,
        URI callbackUri,
        String jdbcUrl,
        Duration authenticationTimeout) {

    static RemoteDatabaseBootstrapProperties from(Environment environment) {
        String vaultUri = required(environment, "app.remote-database.vault-uri");
        String oidcRole = environment.getProperty(
                "app.remote-database.oidc-role",
                "library-reviewer");
        String databaseRole = environment.getProperty(
                "app.remote-database.database-role",
                "library-app");
        String callbackUri = environment.getProperty(
                "app.remote-database.callback-uri",
                "http://localhost:8250/oidc/callback");
        String jdbcUrl = required(environment, "app.remote-database.jdbc-url");
        Duration authenticationTimeout = environment.getProperty(
                "app.remote-database.authentication-timeout",
                Duration.class,
                Duration.ofMinutes(5));

        URI parsedVaultUri = URI.create(vaultUri);
        URI parsedCallbackUri = URI.create(callbackUri);

        if (!"https".equalsIgnoreCase(parsedVaultUri.getScheme())) {
            throw new IllegalArgumentException(
                    "app.remote-database.vault-uri must use HTTPS");
        }
        if (!"http".equalsIgnoreCase(parsedCallbackUri.getScheme())) {
            throw new IllegalArgumentException(
                    "app.remote-database.callback-uri must use HTTP on localhost");
        }
        if (!isLoopbackHost(parsedCallbackUri.getHost())) {
            throw new IllegalArgumentException(
                    "app.remote-database.callback-uri must use localhost or a loopback address");
        }
        if (parsedCallbackUri.getPort() < 1) {
            throw new IllegalArgumentException(
                    "app.remote-database.callback-uri must include a port");
        }
        if (parsedCallbackUri.getPath() == null || parsedCallbackUri.getPath().isBlank()) {
            throw new IllegalArgumentException(
                    "app.remote-database.callback-uri must include a callback path");
        }

        return new RemoteDatabaseBootstrapProperties(
                stripTrailingSlash(parsedVaultUri),
                oidcRole,
                databaseRole,
                parsedCallbackUri,
                jdbcUrl,
                authenticationTimeout);
    }

    private static String required(Environment environment, String key) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required property: " + key);
        }
        return value.trim();
    }

    private static boolean isLoopbackHost(String host) {
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host)
                || "[::1]".equals(host);
    }

    private static URI stripTrailingSlash(URI uri) {
        String value = uri.toString();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return URI.create(value);
    }
}
