package tn.esprit.lessonservice.services;

import tn.esprit.lessonservice.entities.LessonType;
import org.springframework.web.multipart.MultipartFile;

public interface IFileStorageService {
    String storeLessonContent(MultipartFile file, LessonType type);
    void deleteFile(String relativePath);
}
