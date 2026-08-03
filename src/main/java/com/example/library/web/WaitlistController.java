package com.example.library.web;

import com.example.library.dto.WaitlistDtos.WaitlistResponse;
import com.example.library.service.WaitlistService;
import java.security.Principal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class WaitlistController {

    private final WaitlistService waitlistService;

    public WaitlistController(WaitlistService waitlistService) {
        this.waitlistService = waitlistService;
    }

    @PostMapping("/books/{bookId}/waitlist")
    public ResponseEntity<WaitlistResponse> join(@PathVariable Long bookId, Principal principal) {
        return ResponseEntity.status(201).body(waitlistService.join(bookId, principal.getName()));
    }

    @DeleteMapping("/waitlist/{entryId}")
    public ResponseEntity<Void> cancel(@PathVariable Long entryId, Principal principal) {
        waitlistService.cancel(entryId, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/waitlist/me")
    public List<WaitlistResponse> myEntries(Principal principal) {
        return waitlistService.myEntries(principal.getName());
    }
}
