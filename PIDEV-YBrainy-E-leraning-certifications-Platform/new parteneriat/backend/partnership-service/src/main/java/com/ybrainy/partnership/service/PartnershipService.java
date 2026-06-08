package com.ybrainy.partnership.service;

import com.ybrainy.partnership.dto.PartnershipRequest;
import com.ybrainy.partnership.dto.PartnershipResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PartnershipService {

  PartnershipResponse create(PartnershipRequest request);

  PartnershipResponse update(String id, PartnershipRequest request);

  PartnershipResponse getById(String id);

  Page<PartnershipResponse> getAll(String search, Pageable pageable);

  void delete(String id);

  boolean existsById(String id);
}
