package com.ybrainy.partnership.mapper;

import com.ybrainy.partnership.entity.Partnership;
import com.ybrainy.partnership.dto.PartnershipRequest;
import com.ybrainy.partnership.dto.PartnershipResponse;
import org.springframework.stereotype.Component;

@Component
public class PartnershipMapper {

  public Partnership toEntity(PartnershipRequest req) {
    Partnership p = new Partnership();
    apply(p, req);
    return p;
  }

  public void apply(Partnership target, PartnershipRequest req) {
    target.setName(req.name().trim());
    target.setEmail(req.email().trim().toLowerCase());
    target.setPhone(req.phone() == null ? null : req.phone().trim());
    target.setWebsite(req.website() == null ? null : req.website().trim());
    target.setDescription(req.description() == null ? null : req.description().trim());
    target.setActive(req.active());
  }

  public PartnershipResponse toResponse(Partnership p) {
    return new PartnershipResponse(
        p.getId(),
        p.getName(),
        p.getEmail(),
        p.getPhone(),
        p.getWebsite(),
        p.getDescription(),
        p.isActive(),
        p.getCreatedAt(),
        p.getUpdatedAt());
  }
}
