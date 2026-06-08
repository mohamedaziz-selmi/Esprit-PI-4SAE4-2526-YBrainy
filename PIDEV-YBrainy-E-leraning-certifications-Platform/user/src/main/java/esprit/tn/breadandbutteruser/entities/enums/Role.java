package esprit.tn.breadandbutteruser.entities.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum Role {
    ADMIN,
    INSTRUCTOR,
    STUDENT,
    ENTERPRISE_USER;

    @JsonCreator
    public static Role fromString(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty()) return null;
        normalized = normalized.replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        if ("USER".equals(normalized)) {
            // Backward compatibility for older clients/scripts that still post role=USER.
            return STUDENT;
        }
        try {
            return Role.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown role: " + value);
        }
    }

    @JsonValue
    public String toValue() {
        return name();
    }
}
