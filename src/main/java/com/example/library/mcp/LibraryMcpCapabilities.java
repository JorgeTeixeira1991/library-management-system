package com.example.library.mcp;

import com.example.library.dto.BookDtos.BookResponse;
import com.example.library.dto.LoanDtos.LoanResponse;
import com.example.library.dto.WaitlistDtos.WaitlistResponse;
import com.example.library.service.BookService;
import com.example.library.service.LoanService;
import com.example.library.service.WaitlistService;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.List;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@Profile("mcp")
public class LibraryMcpCapabilities {

    private final BookService bookService;
    private final LoanService loanService;
    private final WaitlistService waitlistService;
    private final ObjectMapper objectMapper;

    public LibraryMcpCapabilities(
            BookService bookService,
            LoanService loanService,
            WaitlistService waitlistService,
            ObjectMapper objectMapper) {
        this.bookService = bookService;
        this.loanService = loanService;
        this.waitlistService = waitlistService;
        this.objectMapper = objectMapper;
    }

    @McpTool(name = "searchBooks", description = "Search active books by title, author, category or description")
    public List<BookResponse> searchBooks(
            @McpToolParam(description = "Natural-language search text", required = true) String query) {
        return bookService.search(query);
    }

    @McpTool(name = "borrowBook", description = "Borrow an available book for the authenticated client")
    public LoanResponse borrowBook(
            @McpToolParam(description = "Book identifier", required = true) Long bookId,
            @McpToolParam(description = "Authenticated client username", required = true) String username) {
        return loanService.borrow(bookId, username);
    }

    @McpTool(name = "returnBook", description = "Return an open loan for the authenticated client")
    public LoanResponse returnBook(
            @McpToolParam(description = "Loan identifier", required = true) Long loanId,
            @McpToolParam(description = "Authenticated client username", required = true) String username) {
        return loanService.returnBook(loanId, username);
    }

    @McpTool(name = "joinWaitlist", description = "Join a FIFO waitlist when a book has no available copies")
    public WaitlistResponse joinWaitlist(
            @McpToolParam(description = "Book identifier", required = true) Long bookId,
            @McpToolParam(description = "Authenticated client username", required = true) String username) {
        return waitlistService.join(bookId, username);
    }

    @McpResource(
            uri = "library://books/{id}",
            name = "Book details",
            description = "Returns the current catalogue and availability details for one book",
            mimeType = "application/json")
    public String bookResource(Long id) throws Exception {
        return objectMapper.writeValueAsString(bookService.get(id));
    }

    @McpPrompt(
            name = "recommend-book",
            description = "Prompt template for recommending a book from a client's borrowing history")
    public GetPromptResult recommendBookPrompt(
            @McpArg(name = "username", description = "Client username", required = true) String username) {
        String prompt = "Recommend a library book for user '" + username
                + "'. Use their borrowing history, preferred authors and categories, and current availability."
                + " Explain the reason for each recommendation.";
        return new GetPromptResult(
                "Book recommendation prompt",
                List.of(new PromptMessage(Role.USER, new TextContent(prompt))));
    }
}
