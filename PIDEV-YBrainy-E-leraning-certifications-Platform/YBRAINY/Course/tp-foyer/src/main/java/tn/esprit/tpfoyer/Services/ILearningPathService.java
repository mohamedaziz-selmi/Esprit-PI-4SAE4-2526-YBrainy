package tn.esprit.tpfoyer.Services;

import tn.esprit.tpfoyer.Dto.LearningPathDTO;
import tn.esprit.tpfoyer.Dto.SaveLearningPathRequestDTO;

import java.util.List;

public interface ILearningPathService {
    LearningPathDTO generate(Long studentId, String goal);
    LearningPathDTO save(SaveLearningPathRequestDTO request);
    List<LearningPathDTO> getSavedPaths(Long studentId);
    void deletePath(Long pathId);
}
