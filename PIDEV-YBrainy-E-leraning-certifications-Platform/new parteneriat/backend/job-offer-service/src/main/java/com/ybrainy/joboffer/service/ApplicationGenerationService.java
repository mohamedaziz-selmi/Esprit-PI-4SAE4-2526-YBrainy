package com.ybrainy.joboffer.service;

import com.ybrainy.joboffer.dto.GenerateApplicationRequest;
import com.ybrainy.joboffer.dto.GenerateApplicationResponse;

public interface ApplicationGenerationService {

  GenerateApplicationResponse generate(GenerateApplicationRequest request);
}

