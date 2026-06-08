package esprit.tn.breadandbutteruser.dto;

import esprit.tn.breadandbutteruser.entities.User;

public record InternalUserResponse(
        Long userId,
        String username,
        String email,
        String firstName,
        String lastName
) {
    public static InternalUserResponse fromUser(User user) {
        return new InternalUserResponse(
                user.getUserId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName()
        );
    }
}

