package esprit.tn.breadandbutteruser.entities;

import esprit.tn.breadandbutteruser.entities.enums.Role;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class RoleAttributeConverter implements AttributeConverter<Role, String> {

    @Override
    public String convertToDatabaseColumn(Role attribute) {
        return attribute != null ? attribute.name() : null;
    }

    @Override
    public Role convertToEntityAttribute(String dbData) {
        return Role.fromString(dbData);
    }
}
