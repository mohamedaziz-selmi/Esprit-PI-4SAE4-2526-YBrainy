package esprit.tn.breadandbutteruser.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SignUpDtoValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDownValidator() {
        if (validatorFactory != null) {
            validatorFactory.close();
        }
    }

    @Test
    void rejectsInvalidSignupFields() {
        SignUpDto dto = SignUpDto.builder()
                .username(" ")
                .email("not-an-email")
                .password("Password123")
                .confirmPassword("Password123")
                .dateOfBirth(LocalDate.now().plusDays(1))
                .age(0)
                .build();

        Set<ConstraintViolation<SignUpDto>> violations = validator.validate(dto);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("username", "email", "dateOfBirth", "age");
    }
}
