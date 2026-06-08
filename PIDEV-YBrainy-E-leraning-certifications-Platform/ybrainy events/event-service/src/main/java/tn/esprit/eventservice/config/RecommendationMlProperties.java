package tn.esprit.eventservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "recommendation.ml")
public class RecommendationMlProperties {

    private String baseUrl = "http://localhost:9010";
    private boolean enabled = true;
    private double preferenceWeight = 0.6;
    private double contentWeight = 0.4;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getPreferenceWeight() {
        return preferenceWeight;
    }

    public void setPreferenceWeight(double preferenceWeight) {
        this.preferenceWeight = preferenceWeight;
    }

    public double getContentWeight() {
        return contentWeight;
    }

    public void setContentWeight(double contentWeight) {
        this.contentWeight = contentWeight;
    }
}
