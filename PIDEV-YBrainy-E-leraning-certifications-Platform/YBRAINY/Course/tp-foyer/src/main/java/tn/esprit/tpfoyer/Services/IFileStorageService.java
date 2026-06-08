package tn.esprit.tpfoyer.Services;

import tn.esprit.tpfoyer.Entities.enums.LessonType;
import org.springframework.web.multipart.MultipartFile;

public interface IFileStorageService {
    String storeThumbnail(MultipartFile file);
    String storeLessonContent(MultipartFile file, LessonType type);
    String storeCertificate(MultipartFile file);
    void deleteFile(String relativePath);
}
