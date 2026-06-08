package tn.esprit.tpfoyer.Services;

import tn.esprit.tpfoyer.Dto.AiSearchIntentDTO;

public interface IAiSearchService {
    AiSearchIntentDTO extractSearchIntent(String query);
}
