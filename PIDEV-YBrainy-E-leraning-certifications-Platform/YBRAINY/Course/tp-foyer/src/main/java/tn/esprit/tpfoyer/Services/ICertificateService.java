package tn.esprit.tpfoyer.Services;

import tn.esprit.tpfoyer.Dto.VerificationResponseDTO;

public interface ICertificateService {
    String generateCertificate(Long courseId, Long studentId);
    VerificationResponseDTO verifyCertificate(String certificateId);
}
