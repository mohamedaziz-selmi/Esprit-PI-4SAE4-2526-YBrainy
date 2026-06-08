package esprit.tn.breadandbutteruser.dto;

import java.util.List;

public record SignUpChallengeResponseDto(
        String token,
        String prompt,
        String helperText,
        String challengeKind,
        String selectedMode,
        String recommendedMode,
        String profileHint,
        String recommendationReason,
        long expiresInSeconds,
        List<ModeOptionDto> availableModes,
        List<ChoiceDto> choices,
        FlagJigsawDto flagJigsaw
) {
    public record ModeOptionDto(
            String key,
            String label,
            String description,
            boolean recommended
    ) {
    }

    public record ChoiceDto(
            String id,
            String label
    ) {
    }

    public record FlagJigsawDto(
            String countryName,
            String flagDataUrl,
            int rows,
            int columns,
            List<PieceDto> pieces
    ) {
    }

    public record PieceDto(
            String id,
            int correctIndex,
            int displayOrder,
            int row,
            int column
    ) {
    }
}
