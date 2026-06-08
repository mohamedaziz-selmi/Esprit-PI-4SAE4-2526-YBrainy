package esprit.tn.breadandbutteruser.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import esprit.tn.breadandbutteruser.entities.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignUpDto {
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    private String firstName;

    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @NotBlank(message = "Password confirmation is required")
    private String confirmPassword;

    @Builder.Default
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Role role = Role.INSTRUCTOR;

    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    private String profilePicture;

    @Min(value = 1, message = "Age must be greater than 0")
    private Integer age;

    private String address;

    private String city;

    private String country;

    private String sex;

    private String companyName;

    private String challengeToken;

    private String challengeMode;

    private String challengeAnswer;
}
