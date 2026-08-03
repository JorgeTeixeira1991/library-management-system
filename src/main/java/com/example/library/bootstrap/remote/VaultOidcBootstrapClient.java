package com.example.library.bootstrap.remote;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.logging.Log;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class VaultOidcBootstrapClient {

    private static final String JSON = "application/json";

    private final RemoteDatabaseBootstrapProperties properties;
    private final Log log;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    VaultOidcBootstrapClient(
            RemoteDatabaseBootstrapProperties properties,
            Log log) {
        this.properties = properties;
        this.log = log;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    VaultDatabaseCredentials authenticate() throws Exception {
        String clientNonce = UUID.randomUUID().toString();
        AuthorizationRequest authorizationRequest = requestAuthorizationUrl(clientNonce);

        CallbackResult callback =
                receiveBrowserCallback(authorizationRequest.authUrl());

        log.info(
                "OIDC browser callback received; "
                        + "exchanging authorization code with Vault"
        );

        if (!Objects.equals(
                authorizationRequest.state(),
                callback.state())) {

            throw new IllegalStateException(
                    "OIDC callback state did not match "
                            + "the authorization request"
            );
        }

        VaultToken vaultToken = exchangeAuthorizationCode(
                callback,
                authorizationRequest.nonce(),
                clientNonce
        );

        log.info(
                "Vault OIDC authentication succeeded; "
                        + "requesting temporary PostgreSQL credentials"
        );

        VaultDatabaseCredentials credentials =
                requestDatabaseCredentials(vaultToken.token());

        log.info(
                "Temporary PostgreSQL credentials obtained successfully"
        );

        return credentials;
    }

    private AuthorizationRequest requestAuthorizationUrl(String clientNonce) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "role", properties.oidcRole(),
                "redirect_uri", properties.callbackUri().toString(),
                "client_nonce", clientNonce));

        HttpRequest request = HttpRequest.newBuilder(
                        vaultApi("/v1/auth/oidc/oidc/auth_url"))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", JSON)
                .header("Content-Type", JSON)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        JsonNode response = sendForJson(request);
        URI authUrl = URI.create(requiredText(response, "/data/auth_url"));
        Map<String, String> query = parseQuery(authUrl.getRawQuery());

        String state = requiredValue(query, "state", "Vault authorization URL");
        String nonce = requiredValue(query, "nonce", "Vault authorization URL");

        return new AuthorizationRequest(authUrl, state, nonce);
    }

    private CallbackResult receiveBrowserCallback(URI authUrl) throws Exception {
        CompletableFuture<CallbackResult> callbackFuture = new CompletableFuture<>();
        URI callbackUri = properties.callbackUri();

        String listenerAddress = "0.0.0.0";

        InetSocketAddress address = new InetSocketAddress(
                listenerAddress,
                callbackUri.getPort());

        log.info(
                "Starting OIDC callback listener on "
                        + listenerAddress
                        + ":"
                        + callbackUri.getPort()
                        + callbackUri.getPath()
        );

        HttpServer callbackServer = HttpServer.create(address, 0);
        ExecutorService callbackExecutor = Executors.newVirtualThreadPerTaskExecutor();

        callbackServer.setExecutor(callbackExecutor);
        callbackServer.createContext(
                callbackUri.getPath(),
                exchange -> handleCallback(exchange, callbackFuture));

        callbackServer.start();

        System.err.println();
        System.err.println("Vault authentication is required.");
        System.err.println("Open this URL in your browser:");
        System.err.println(authUrl);
        System.err.println();

        try {
            log.info("Opening the browser for passwordless email authentication");

            System.err.println();
            System.err.println("Vault authentication is required.");
            System.err.println("Open this URL in your browser:");
            System.err.println(authUrl);
            System.err.println();

            openBrowser(authUrl);

            return callbackFuture.get(
                    properties.authenticationTimeout().toMillis(),
                    TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            throw new IllegalStateException(
                    "Timed out waiting for OIDC authentication callback after "
                            + properties.authenticationTimeout(),
                    exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();

            if (cause instanceof Exception typedCause) {
                throw typedCause;
            }

            throw new IllegalStateException(
                    "OIDC callback failed",
                    cause);
        } finally {
            callbackServer.stop(0);
            callbackExecutor.close();
        }
    }

    private void handleCallback(
            HttpExchange exchange,
            CompletableFuture<CallbackResult> callbackFuture)
            throws IOException {

        log.info(
                "Received OIDC callback from "
                        + exchange.getRemoteAddress()
                        + ": "
                        + exchange.getRequestURI()
        );

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendHtml(
                    exchange,
                    405,
                    "Authentication callback only accepts GET requests."
            );
            return;
        }

        Map<String, String> query =
                parseQuery(exchange.getRequestURI().getRawQuery());

        String error = query.get("error");

        if (error != null && !error.isBlank()) {
            String description =
                    query.getOrDefault("error_description", error);

            IllegalStateException exception =
                    new IllegalStateException(
                            "OIDC provider rejected authentication: "
                                    + description
                    );

            try {
                sendHtml(
                        exchange,
                        401,
                        "Authentication was not completed. "
                                + "Return to the application."
                );
            } finally {
                callbackFuture.completeExceptionally(exception);
            }

            return;
        }

        String code = query.get("code");
        String state = query.get("state");

        if (code == null
                || code.isBlank()
                || state == null
                || state.isBlank()) {

            IllegalStateException exception =
                    new IllegalStateException(
                            "OIDC callback did not contain code and state"
                    );

            try {
                sendHtml(
                        exchange,
                        400,
                        "Invalid authentication callback. "
                                + "Return to the application."
                );
            } finally {
                callbackFuture.completeExceptionally(exception);
            }

            return;
        }

        CallbackResult result = new CallbackResult(code, state);

        try {
            sendHtml(
                    exchange,
                    200,
                    "Authentication succeeded. "
                            + "You may close this tab and return to the application."
            );
        } finally {
            callbackFuture.complete(result);
        }
    }

    private VaultToken exchangeAuthorizationCode(
            CallbackResult callback,
            String nonce,
            String clientNonce) throws Exception {

        String query = "state=" + encode(callback.state())
                + "&nonce=" + encode(nonce)
                + "&code=" + encode(callback.code())
                + "&client_nonce=" + encode(clientNonce);

        HttpRequest request = HttpRequest.newBuilder(
                        vaultApi("/v1/auth/oidc/oidc/callback?" + query))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", JSON)
                .GET()
                .build();

        JsonNode response = sendForJson(request);
        String token = requiredText(response, "/auth/client_token");
        long leaseDuration = response.at("/auth/lease_duration").asLong();
        boolean renewable = response.at("/auth/renewable").asBoolean();

        return new VaultToken(token, leaseDuration, renewable);
    }

    private VaultDatabaseCredentials requestDatabaseCredentials(String vaultToken)
            throws Exception {

        String encodedRole = encodePathSegment(properties.databaseRole());

        HttpRequest request = HttpRequest.newBuilder(
                        vaultApi("/v1/database/creds/" + encodedRole))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", JSON)
                .header("X-Vault-Token", vaultToken)
                .GET()
                .build();

        JsonNode response = sendForJson(request);

        return new VaultDatabaseCredentials(
                requiredText(response, "/data/username"),
                requiredText(response, "/data/password"),
                requiredText(response, "/lease_id"),
                response.path("lease_duration").asLong(),
                response.path("renewable").asBoolean());
    }

    private JsonNode sendForJson(HttpRequest request) throws Exception {
        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String safeBody = response.body();
            if (safeBody.length() > 2_000) {
                safeBody = safeBody.substring(0, 2_000) + "...";
            }
            throw new IllegalStateException(
                    "Vault request failed with HTTP "
                            + response.statusCode()
                            + ": "
                            + safeBody);
        }

        return objectMapper.readTree(response.body());
    }

    private URI vaultApi(String path) {
        return URI.create(properties.vaultUri() + path);
    }

    private void openBrowser(URI uri) {
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri);
                return;
            }
        } catch (Exception exception) {
            log.debug("Java Desktop browser launch was unavailable", exception);
        }

        String osName = System.getProperty("os.name", "").toLowerCase();

        boolean wsl = System.getenv("WSL_DISTRO_NAME") != null
                || System.getenv("WSL_INTEROP") != null;

        if (wsl) {
            if (tryStart("wslview", uri.toString())
                    || tryStart("explorer.exe", uri.toString())) {
                return;
            }
        }

        if (osName.contains("win")) {
            if (tryStart(
                    "rundll32",
                    "url.dll,FileProtocolHandler",
                    uri.toString())) {
                return;
            }
        } else if (osName.contains("mac")) {
            if (tryStart("open", uri.toString())) {
                return;
            }
        } else if (tryStart("xdg-open", uri.toString())) {
            return;
        }

        log.warn(
                "Unable to open the browser automatically. "
                        + "Open this URL manually: "
                        + uri
        );
    }

    private boolean tryStart(String... command) {
        try {
            new ProcessBuilder(command).start();
            return true;
        } catch (IOException exception) {
            log.debug("Browser launcher was unavailable: " + command[0]);
            return false;
        }
    }

    private static void sendHtml(HttpExchange exchange, int status, String message)
            throws IOException {
        String escaped = message
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");

        String html = """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Library authentication</title>
                </head>
                <body>
                  <h2>Library authentication</h2>
                  <p>%s</p>
                  <script>setTimeout(() => window.close(), 1500);</script>
                </body>
                </html>
                """.formatted(escaped);

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> values = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return values;
        }

        for (String pair : rawQuery.split("&")) {
            int separator = pair.indexOf('=');
            String rawName = separator >= 0 ? pair.substring(0, separator) : pair;
            String rawValue = separator >= 0 ? pair.substring(separator + 1) : "";
            values.put(decode(rawName), decode(rawValue));
        }
        return values;
    }

    private static String requiredValue(
            Map<String, String> values,
            String key,
            String source) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(source + " did not contain " + key);
        }
        return value;
    }

    private static String requiredText(JsonNode root, String pointer) {
        JsonNode node = root.at(pointer);
        String value = node.isMissingNode() || node.isNull() ? null : node.asText();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Vault response did not contain " + pointer);
        }
        return value;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String encodePathSegment(String value) {
        return encode(value).replace("+", "%20");
    }

    private record AuthorizationRequest(URI authUrl, String state, String nonce) {
    }

    private record CallbackResult(String code, String state) {
    }

    private record VaultToken(String token, long leaseDurationSeconds, boolean renewable) {
    }
}
