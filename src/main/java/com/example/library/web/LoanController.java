package com.example.library.web;

import com.example.library.dto.LoanDtos.LoanResponse;
import com.example.library.service.LoanService;
import java.security.Principal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @PostMapping("/books/{bookId}/borrow")
    public ResponseEntity<LoanResponse> borrow(@PathVariable Long bookId, Principal principal) {
        return ResponseEntity.status(201).body(loanService.borrow(bookId, principal.getName()));
    }

    @PostMapping("/loans/{loanId}/return")
    public LoanResponse returnBook(@PathVariable Long loanId, Principal principal) {
        return loanService.returnBook(loanId, principal.getName());
    }

    @GetMapping("/loans/me")
    public List<LoanResponse> myLoans(Principal principal) {
        return loanService.myLoans(principal.getName());
    }
}
