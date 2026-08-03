package com.example.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.example.library.repository.BookRepository;
import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.fail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("remote")
@Tag("raspberry-pi")
class RaspberryPiDatabaseIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(RaspberryPiDatabaseIntegrationTest.class);
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private DataSource dataSource;

    @Test
    void connectsToRaspberryPiPostgres() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                select
                    current_database(),
                    current_user,
                    inet_server_addr(),
                    inet_server_port()
                """)) {

            assertThat(result.next()).isTrue();

            String database = result.getString(1);
            String username = result.getString(2);
            String serverAddress = result.getString(3);
            int serverPort = result.getInt(4);

            logger.info("Connected to database={}, user={}, server={}:{}", database, username, serverAddress, serverPort);

            assertThat(database).isEqualTo("library_review");
            assertThat(username).startsWith("v-");

        } catch (Exception exception) {
            logger.error("Failed to connect to the Raspberry Pi PostgreSQL database", exception);
            fail("Could not connect to the Raspberry Pi PostgreSQL database", exception);
        }
    }

    @Test
    void readsBooksFromRaspberryPi() {
        long count = bookRepository.count();

        System.out.println("Books in Raspberry Pi database: " + count);

        assertThat(count).isGreaterThan(1_000_000);
    }

    @Test
    void searchesBooksThroughTheRealApplication() {
        try {
            mockMvc.perform(get("/api/v1/books").queryParam("query", "java")
                    .with(httpBasic("client", "client123")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());

        } catch (Exception exception) {
            logger.error("Book search endpoint failed against the Raspberry Pi database", exception);
            fail("Book search endpoint failed", exception);
        }
    }

    @Test
    void anonymousRequestIsRejected() {
        try {
            mockMvc.perform(get("/api/v1/books")).andExpect(status().isUnauthorized());

        } catch (Exception exception) {
            logger.error("Failed while testing anonymous access rejection", exception);
            fail("Anonymous access security test failed", exception);
        }
    }
}

