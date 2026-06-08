package esprit.tn.breadandbutteruser.entities.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum IntegrityStatus {
    SECURE,
    WARNED,
    SUSPENDED,
    FLAGGED;

    @JsonCreator
    public static IntegrityStatus fromString(String value) {
        if (value == null) return null;
        try {
            return IntegrityStatus.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown integrity status: " + value);
        }
    }

    @JsonValue
    public String toValue() {
        return name();
    }
}