package com.example.library.web;

import com.example.library.dto.LateFeeDtos.LateFeeResponse;
import com.example.library.service.LateFeeService;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/late-fees")
public class LateFeeController {

    private final LateFeeService lateFeeService;

    public LateFeeController(LateFeeService lateFeeService) {
        this.lateFeeService = lateFeeService;
    }

    @GetMapping("/me")
    public List<LateFeeResponse> myFees(Principal principal) {
        return lateFeeService.myFees(principal.getName());
    }
}
