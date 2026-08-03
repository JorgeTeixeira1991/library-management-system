package com.example.library.bootstrap.remote;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Obtains short-lived PostgreSQL credentials from Vault before Spring Boot
 * creates the application context and datasource.
 */
public final class RemoteDatabaseEnvironmentPostProcessor
        implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "vaultRemoteDatabaseCredentials";

    private final Log log;

    public RemoteDatabaseEnvironmentPostProcessor(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(RemoteDatabaseEnvironmentPostProcessor.class);
    }

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment,
            SpringApplication application) {

        boolean enabled = environment.getProperty(
                "app.remote-database.enabled",
                Boolean.class,
                false);

        if (!enabled) {
            return;
        }

        RemoteDatabaseBootstrapProperties properties =
                RemoteDatabaseBootstrapProperties.from(environment);

        log.info("Remote database mode is enabled; authenticating through Vault OIDC");

        try {
            VaultDatabaseCredentials credentials =
                    new VaultOidcBootstrapClient(properties, log).authenticate();

            Map<String, Object> datasourceProperties = new LinkedHashMap<>();
            datasourceProperties.put("spring.datasource.url", properties.jdbcUrl());
            datasourceProperties.put("spring.datasource.username", credentials.username());
            datasourceProperties.put("spring.datasource.password", credentials.password());
            datasourceProperties.put("spring.flyway.enabled", false);

            environment.getPropertySources().addFirst(
                    new MapPropertySource(PROPERTY_SOURCE_NAME, datasourceProperties));

            log.info("Temporary PostgreSQL credentials obtained from Vault; continuing startup");
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to obtain remote PostgreSQL credentials from Vault",
                    exception);
        }
    }

    /**
     * Run immediately after application.yml and profile-specific configuration
     * have been loaded, but before the application context is refreshed.
     */
    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }
}
