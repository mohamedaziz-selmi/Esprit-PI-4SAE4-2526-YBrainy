package com.ybrainy.joboffer.controller;

import com.ybrainy.joboffer.dto.GenerateApplicationRequest;
import com.ybrainy.joboffer.dto.GenerateApplicationResponse;
import com.ybrainy.joboffer.service.ApplicationGenerationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ApplicationGenerationController {

  private final ApplicationGenerationService applicationGenerationService;

  public ApplicationGenerationController(ApplicationGenerationService applicationGenerationService) {
    this.applicationGenerationService = applicationGenerationService;
  }

  @PostMapping("/generate-application")
  public GenerateApplicationResponse generate(@Valid @RequestBody GenerateApplicationRequest request) {
    return applicationGenerationService.generate(request);
  }
}

