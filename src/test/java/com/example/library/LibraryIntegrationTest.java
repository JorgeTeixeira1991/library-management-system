package com.example.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.library.domain.WaitlistStatus;
import com.example.library.repository.WaitlistRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.function.ThrowingSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class LibraryIntegrationTest {

    private static final Logger logger =
            LoggerFactory.getLogger(LibraryIntegrationTest.class);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine");

    private final MockMvc mockMvc;
    private final WaitlistRepository waitlistRepository;

    LibraryIntegrationTest(
            MockMvc mockMvc,
            WaitlistRepository waitlistRepository
    ) {
        this.mockMvc = mockMvc;
        this.waitlistRepository = waitlistRepository;
    }

    @Test
    void healthEndpointIsPublic() {
        runLoggedTest("Health endpoint is public", () ->
                mockMvc.perform(get("/actuator/health"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("UP"))
        );
    }

    @Test
    void ownerCanCreateButClientCannotCreateBooks() {
        runLoggedTest(
                "Owner can create books but client cannot",
                () -> {
                    String payload = """
                            {
                              "isbn": "9780201633610",
                              "title": "Design Patterns",
                              "authors": "Erich Gamma, Richard Helm, Ralph Johnson, John Vlissides",
                              "category": "Architecture",
                              "description": "Elements of reusable object-oriented software.",
                              "totalCopies": 2
                            }
                            """;

                    mockMvc.perform(
                                    post("/api/v1/books")
                                            .with(httpBasic(
                                                    "client",
                                                    "client123"
                                            ))
                                            .contentType(
                                                    MediaType.APPLICATION_JSON
                                            )
                                            .content(payload)
                            )
                            .andExpect(status().isForbidden());

                    logger.info(
                            "Authorization check passed: client cannot create books"
                    );

                    mockMvc.perform(
                                    post("/api/v1/books")
                                            .with(httpBasic(
                                                    "owner",
                                                    "owner123"
                                            ))
                                            .contentType(
                                                    MediaType.APPLICATION_JSON
                                            )
                                            .content(payload)
                            )
                            .andExpect(status().isCreated())
                            .andExpect(
                                    jsonPath("$.title")
                                            .value("Design Patterns")
                            );

                    logger.info(
                            "Authorization check passed: owner can create books"
                    );
                }
        );
    }

    @Test
    void borrowReturnAndWaitlistPromotionWorkEndToEnd() {
        runLoggedTest(
                "Borrow, return and waitlist promotion workflow",
                () -> {
                    String createPayload = """
                            {
                              "isbn": "9780000000002",
                              "title": "Single Copy Demo Book",
                              "authors": "Demo Author",
                              "category": "Demo",
                              "description": "A one-copy title used by the integration test.",
                              "totalCopies": 1
                            }
                            """;

                    MvcResult createResult = runLoggedStep(
                            "Create a one-copy test book",
                            () -> mockMvc.perform(
                                            post("/api/v1/books")
                                                    .with(httpBasic(
                                                            "owner",
                                                            "owner123"
                                                    ))
                                                    .contentType(
                                                            MediaType.APPLICATION_JSON
                                                    )
                                                    .content(createPayload)
                                    )
                                    .andExpect(status().isCreated())
                                    .andReturn()
                    );

                    Number bookId = JsonPath.read(
                            createResult
                                    .getResponse()
                                    .getContentAsString(),
                            "$.id"
                    );

                    logger.info(
                            "Created test book with ID {}",
                            bookId
                    );

                    MvcResult borrowResult = runLoggedStep(
                            "Borrow the only available copy",
                            () -> mockMvc.perform(
                                            post(
                                                    "/api/v1/books/{id}/borrow",
                                                    bookId.longValue()
                                            )
                                                    .with(httpBasic(
                                                            "client",
                                                            "client123"
                                                    ))
                                    )
                                    .andExpect(status().isCreated())
                                    .andExpect(
                                            jsonPath("$.status")
                                                    .value("OPEN")
                                    )
                                    .andReturn()
                    );

                    Number loanId = JsonPath.read(
                            borrowResult
                                    .getResponse()
                                    .getContentAsString(),
                            "$.id"
                    );

                    logger.info(
                            "Created loan with ID {}",
                            loanId
                    );

                    runLoggedStep(
                            "Add second client to the waitlist",
                            () -> mockMvc.perform(
                                            post(
                                                    "/api/v1/books/{id}/waitlist",
                                                    bookId.longValue()
                                            )
                                                    .with(httpBasic(
                                                            "client2",
                                                            "client2123"
                                                    ))
                                    )
                                    .andExpect(status().isCreated())
                                    .andExpect(
                                            jsonPath("$.status")
                                                    .value("WAITING")
                                    )
                    );

                    runLoggedStep(
                            "Return the borrowed book",
                            () -> mockMvc.perform(
                                            post(
                                                    "/api/v1/loans/{id}/return",
                                                    loanId.longValue()
                                            )
                                                    .with(httpBasic(
                                                            "client",
                                                            "client123"
                                                    ))
                                    )
                                    .andExpect(status().isOk())
                                    .andExpect(
                                            jsonPath("$.status")
                                                    .value("RETURNED")
                                    )
                    );

                    runLoggedStep(
                            "Verify waitlist promotion",
                            () -> assertThat(
                                    waitlistRepository
                                            .findByUserUsernameOrderByRequestedAtDesc(
                                                    "client2"
                                            )
                            ).anyMatch(
                                    entry ->
                                            entry.getStatus()
                                                    == WaitlistStatus.NOTIFIED
                            )
                    );
                }
        );
    }

    @Test
    void fullTextAndTrigramSearchReturnsRelevantBooks() {
        runLoggedTest(
                "Full-text and trigram search returns relevant books",
                () -> mockMvc.perform(
                                get("/api/v1/books")
                                        .param(
                                                "query",
                                                "Effective Jvaa"
                                        )
                                        .with(httpBasic(
                                                "client",
                                                "client123"
                                        ))
                        )
                        .andExpect(status().isOk())
                        .andExpect(
                                jsonPath("$[0].title")
                                        .value("Effective Java")
                        )
        );
    }

    private void runLoggedTest(
            String testDescription,
            Executable test
    ) {
        try {
            test.execute();

            logger.info(
                    "TEST PASSED: {}",
                    testDescription
            );

        } catch (Throwable throwable) {
            logger.error(
                    "TEST FAILED: {}",
                    testDescription,
                    throwable
            );

            fail(
                    testDescription + " failed",
                    throwable
            );
        }
    }

    private void runLoggedStep(
            String stepDescription,
            Executable step
    ) {
        try {
            step.execute();

            logger.info(
                    "STEP PASSED: {}",
                    stepDescription
            );

        } catch (Throwable throwable) {
            logger.error(
                    "STEP FAILED: {}",
                    stepDescription,
                    throwable
            );

            fail(
                    stepDescription + " failed",
                    throwable
            );
        }
    }

    private <T> T runLoggedStep(
            String stepDescription,
            ThrowingSupplier<T> step
    ) {
        try {
            T result = step.get();

            logger.info(
                    "STEP PASSED: {}",
                    stepDescription
            );

            return result;

        } catch (Throwable throwable) {
            logger.error(
                    "STEP FAILED: {}",
                    stepDescription,
                    throwable
            );

            return fail(
                    stepDescription + " failed",
                    throwable
            );
        }
    }
}

