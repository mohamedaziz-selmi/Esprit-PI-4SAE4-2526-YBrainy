package tn.esprit.inscriptionservice.dto;

public record UserDto(
        Long userId,
        String username,
        String email,
        String firstName,
        String lastName
) {
}
