package com.example.library.web;

import com.example.library.dto.BookDtos.BookResponse;
import com.example.library.dto.BookDtos.CreateBookRequest;
import com.example.library.dto.BookDtos.UpdateBookRequest;
import com.example.library.dto.EnrichmentDtos.EnrichmentJobResponse;
import com.example.library.dto.LoanDtos.LoanResponse;
import com.example.library.service.BookService;
import com.example.library.service.MetadataEnrichmentService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/books")
public class BookController {

    private final BookService bookService;
    private final MetadataEnrichmentService enrichmentService;

    public BookController(BookService bookService, MetadataEnrichmentService enrichmentService) {
        this.bookService = bookService;
        this.enrichmentService = enrichmentService;
    }

    @GetMapping
    public List<BookResponse> search(@RequestParam(defaultValue = "") String query) {
        return bookService.search(query);
    }

    @GetMapping("/{id}")
    public BookResponse get(@PathVariable Long id) {
        return bookService.get(id);
    }

    @PostMapping
    public ResponseEntity<BookResponse> create(@Valid @RequestBody CreateBookRequest request) {
        BookResponse created = bookService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/books/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public BookResponse update(@PathVariable Long id, @Valid @RequestBody UpdateBookRequest request) {
        return bookService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        bookService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/history")
    public List<LoanResponse> history(@PathVariable Long id) {
        return bookService.history(id);
    }

    @PostMapping("/{id}/enrich")
    public ResponseEntity<EnrichmentJobResponse> enrich(@PathVariable Long id) {
        return ResponseEntity.accepted().body(enrichmentService.enrich(id));
    }

    @GetMapping("/{id}/enrichment-jobs")
    public List<EnrichmentJobResponse> enrichmentHistory(@PathVariable Long id) {
        return enrichmentService.history(id);
    }
}
