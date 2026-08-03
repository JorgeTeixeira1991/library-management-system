package com.example.library.web;

import com.example.library.dto.RecommendationDtos.RecommendationResponse;
import com.example.library.service.RecommendationService;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/me")
    public List<RecommendationResponse> myRecommendations(Principal principal) {
        return recommendationService.recommend(principal.getName());
    }
}
