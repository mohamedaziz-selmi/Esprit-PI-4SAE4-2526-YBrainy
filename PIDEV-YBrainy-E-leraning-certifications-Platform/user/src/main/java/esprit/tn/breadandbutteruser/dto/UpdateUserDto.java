package esprit.tn.breadandbutteruser.dto;

import jakarta.validation.constraints.Min;
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
public class UpdateUserDto {
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    private String firstName;

    private String lastName;

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
}
