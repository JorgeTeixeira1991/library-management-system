package com.example.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.library.domain.WaitlistStatus;
import com.example.library.repository.WaitlistRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;


@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class LibraryIntegrationTest {

    public LibraryIntegrationTest(MockMvc mockMvc, WaitlistRepository waitlistRepository) {
        this.mockMvc = mockMvc;
        this.waitlistRepository = waitlistRepository;
    }

    private final Logger logger = LoggerFactory.getLogger(LibraryIntegrationTest.class);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private final MockMvc mockMvc;

    private final WaitlistRepository waitlistRepository;

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void ownerCanCreateButClientCannotCreateBooks() {
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

        try {
            mockMvc.perform(post("/api/v1/books")
                            .with(httpBasic("client", "client123"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isForbidden());

            logger.info("Client cannot create books. Test is successful");
        } catch (Exception e) {
            logger.info("Exception during the first test: " + e.getMessage());
            throw new RuntimeException(e);
        }

        try {
            mockMvc.perform(post("/api/v1/books")
                            .with(httpBasic("owner", "owner123"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.title").value("Design Patterns"));

            logger.info("Owner can create books. Test is successful");
        } catch (Exception e) {
            logger.info("Exception during the second test: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }


    @Test
    void borrowReturnAndWaitlistPromotionWorkEndToEnd() throws Exception {
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

        MvcResult createResult = mockMvc.perform(post("/api/v1/books")
                        .with(httpBasic("owner", "owner123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andReturn();
        Number bookId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        MvcResult borrowResult = mockMvc.perform(post("/api/v1/books/{id}/borrow", bookId.longValue())
                        .with(httpBasic("client", "client123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn();
        Number loanId = JsonPath.read(borrowResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/v1/books/{id}/waitlist", bookId.longValue())
                        .with(httpBasic("client2", "client2123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("WAITING"));

        mockMvc.perform(post("/api/v1/loans/{id}/return", loanId.longValue())
                        .with(httpBasic("client", "client123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"));

        assertThat(waitlistRepository.findByUserUsernameOrderByRequestedAtDesc("client2"))
                .anyMatch(entry -> entry.getStatus() == WaitlistStatus.NOTIFIED);
    }

    @Test
    void fullTextAndTrigramSearchReturnsRelevantBooks() throws Exception {
        mockMvc.perform(get("/api/v1/books")
                        .param("query", "Effective Jvaa")
                        .with(httpBasic("client", "client123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Effective Java"));
    }
}
