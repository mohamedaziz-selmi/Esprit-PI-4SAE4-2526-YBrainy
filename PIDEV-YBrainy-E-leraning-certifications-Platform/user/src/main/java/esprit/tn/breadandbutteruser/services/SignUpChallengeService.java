package esprit.tn.breadandbutteruser.services;

import esprit.tn.breadandbutteruser.dto.SignUpChallengeRequestDto;
import esprit.tn.breadandbutteruser.dto.SignUpChallengeResponseDto;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class SignUpChallengeService {
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
    private static final int CHOICE_COUNT = 4;
    private static final int JIGSAW_ROWS = 2;
    private static final int JIGSAW_COLUMNS = 3;

    private final SecureRandom random = new SecureRandom();
    private final ConcurrentMap<String, StoredChallenge> storedChallenges = new ConcurrentHashMap<>();

    public SignUpChallengeResponseDto createChallenge(SignUpChallengeRequestDto request) {
        cleanupExpiredChallenges();

        Integer age = sanitizeAge(request.age());
        String countryKey = normalizeCountryKey(request.country());
        FlagDefinition flagDefinition = resolveFlagDefinition(countryKey);

        ChallengeMode recommendedMode = recommendMode(age, flagDefinition != null);
        List<ChallengeMode> availableModes = buildAvailableModes(recommendedMode, flagDefinition != null);
        ChallengeMode selectedMode = resolveSelectedMode(request.preferredMode(), recommendedMode, availableModes);

        GeneratedChallenge generatedChallenge = generateChallenge(selectedMode, countryKey, flagDefinition);
        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(CHALLENGE_TTL);

        storedChallenges.put(token, new StoredChallenge(
                selectedMode.key(),
                generatedChallenge.expectedAnswer(),
                expiresAt
        ));

        return new SignUpChallengeResponseDto(
                token,
                generatedChallenge.prompt(),
                buildHelperText(selectedMode, generatedChallenge),
                generatedChallenge.kind(),
                selectedMode.key(),
                recommendedMode.key(),
                buildProfileHint(age, countryKey),
                buildRecommendationReason(recommendedMode, age, countryKey, flagDefinition != null),
                CHALLENGE_TTL.toSeconds(),
                availableModes.stream()
                        .map(mode -> new SignUpChallengeResponseDto.ModeOptionDto(
                                mode.key(),
                                mode.label(),
                                mode.description(),
                                mode == recommendedMode
                        ))
                        .toList(),
                generatedChallenge.choices().stream()
                        .map(choice -> new SignUpChallengeResponseDto.ChoiceDto(choice.id(), choice.label()))
                        .toList(),
                generatedChallenge.flagJigsaw()
        );
    }

    public void validateChallenge(String token, String mode, String answer) {
        cleanupExpiredChallenges();

        if (!StringUtils.hasText(token) || !StringUtils.hasText(mode) || !StringUtils.hasText(answer)) {
            throw new RuntimeException("Complete the personalized security check before creating an account.");
        }

        StoredChallenge storedChallenge = storedChallenges.remove(token.trim());
        if (storedChallenge == null || storedChallenge.expiresAt().isBefore(Instant.now())) {
            throw new RuntimeException("Your personalized security check expired. Please try a new challenge.");
        }

        String normalizedMode = mode.trim().toLowerCase(Locale.ROOT);
        if (!storedChallenge.mode().equals(normalizedMode)) {
            throw new RuntimeException("The selected challenge no longer matches. Please try a new challenge.");
        }

        if (!storedChallenge.expectedAnswer().equals(answer.trim())) {
            throw new RuntimeException("That challenge answer was incorrect. Please try a different challenge.");
        }
    }

    private GeneratedChallenge generateChallenge(ChallengeMode mode, String countryKey, FlagDefinition flagDefinition) {
        return switch (mode) {
            case JIGSAW -> flagDefinition != null
                    ? generateFlagJigsawChallenge(flagDefinition)
                    : generateLogicChallenge();
            case HISTORY -> generateHistoryChallenge(countryKey);
            case MATH -> generateMathChallenge();
            case LOGIC -> generateLogicChallenge();
        };
    }

    private GeneratedChallenge generateMathChallenge() {
        List<MathQuestion> bank = List.of(
                new MathQuestion("Solve the check: 14 + 9 = ?", List.of("23", "21", "25", "19"), "23"),
                new MathQuestion("Solve the check: 18 - 7 = ?", List.of("11", "12", "10", "13"), "11"),
                new MathQuestion("Solve the check: 6 x 4 = ?", List.of("24", "20", "26", "18"), "24"),
                new MathQuestion("Solve the check: 27 / 3 = ?", List.of("9", "8", "6", "12"), "9"),
                new MathQuestion("Which value makes 5 + ? = 17?", List.of("12", "10", "11", "13"), "12")
        );

        MathQuestion question = bank.get(random.nextInt(bank.size()));
        return multipleChoiceChallenge("multiple_choice", question.prompt(), question.answers(), question.correctAnswer());
    }

    private GeneratedChallenge generateLogicChallenge() {
        List<LogicQuestion> bank = List.of(
                new LogicQuestion("Complete the pattern: 3, 6, 12, 24, ?", List.of("48", "36", "40", "52"), "48"),
                new LogicQuestion("Complete the pattern: 5, 10, 15, 20, ?", List.of("25", "30", "24", "18"), "25"),
                new LogicQuestion("Which number does not belong: 2, 4, 8, 14, 16", List.of("14", "16", "8", "4"), "14"),
                new LogicQuestion("Complete the pattern: 2, 5, 11, 23, ?", List.of("47", "45", "39", "51"), "47"),
                new LogicQuestion("Which shape group has one extra side: triangle, square, pentagon, hexagon?", List.of("triangle", "square", "pentagon", "hexagon"), "hexagon")
        );

        LogicQuestion question = bank.get(random.nextInt(bank.size()));
        return multipleChoiceChallenge("multiple_choice", question.prompt(), question.answers(), question.correctAnswer());
    }

    private GeneratedChallenge generateHistoryChallenge(String countryKey) {
        List<HistoryQuestion> questions = HISTORY_BANK.getOrDefault(countryKey, GENERAL_HISTORY_BANK);
        HistoryQuestion question = questions.get(random.nextInt(questions.size()));
        return multipleChoiceChallenge("multiple_choice", question.prompt(), question.answers(), question.correctAnswer());
    }

    private GeneratedChallenge generateFlagJigsawChallenge(FlagDefinition flagDefinition) {
        String prompt = "Rebuild the " + flagDefinition.countryName() + " flag by swapping the pieces into the correct order.";
        List<SignUpChallengeResponseDto.PieceDto> pieces = new ArrayList<>();
        List<Integer> displayOrder = new ArrayList<>();
        for (int index = 0; index < JIGSAW_ROWS * JIGSAW_COLUMNS; index++) {
            displayOrder.add(index);
        }

        do {
            Collections.shuffle(displayOrder, random);
        } while (isSolvedOrder(displayOrder));

        for (int index = 0; index < JIGSAW_ROWS * JIGSAW_COLUMNS; index++) {
            int row = index / JIGSAW_COLUMNS;
            int column = index % JIGSAW_COLUMNS;
            pieces.add(new SignUpChallengeResponseDto.PieceDto(
                    "piece-" + (index + 1),
                    index,
                    displayOrder.indexOf(index),
                    row,
                    column
            ));
        }

        String expectedAnswer = pieces.stream()
                .sorted((left, right) -> Integer.compare(left.correctIndex(), right.correctIndex()))
                .map(SignUpChallengeResponseDto.PieceDto::id)
                .reduce((left, right) -> left + "|" + right)
                .orElse("");

        SignUpChallengeResponseDto.FlagJigsawDto flagJigsaw = new SignUpChallengeResponseDto.FlagJigsawDto(
                flagDefinition.countryName(),
                flagDefinition.flagDataUrl(),
                JIGSAW_ROWS,
                JIGSAW_COLUMNS,
                pieces
        );

        return new GeneratedChallenge(
                "flag_jigsaw",
                prompt,
                List.of(),
                flagJigsaw,
                expectedAnswer
        );
    }

    private GeneratedChallenge multipleChoiceChallenge(
            String kind,
            String prompt,
            List<String> rawChoices,
            String correctLabel
    ) {
        List<String> shuffledChoices = new ArrayList<>(rawChoices);
        Collections.shuffle(shuffledChoices, random);

        List<ChallengeChoice> choices = new ArrayList<>();
        String expectedAnswer = null;
        for (int index = 0; index < shuffledChoices.size(); index++) {
            String id = "choice-" + (index + 1);
            String label = shuffledChoices.get(index);
            choices.add(new ChallengeChoice(id, label));
            if (label.equals(correctLabel)) {
                expectedAnswer = id;
            }
        }

        if (expectedAnswer == null) {
            throw new IllegalStateException("Could not prepare the personalized challenge choices.");
        }

        return new GeneratedChallenge(kind, prompt, choices, null, expectedAnswer);
    }

    private ChallengeMode recommendMode(Integer age, boolean flagAvailable) {
        if (age != null && age >= 50) {
            return ChallengeMode.HISTORY;
        }
        if (age != null && age <= 25) {
            return ChallengeMode.MATH;
        }
        if (flagAvailable) {
            return ChallengeMode.JIGSAW;
        }
        return ChallengeMode.LOGIC;
    }

    private List<ChallengeMode> buildAvailableModes(ChallengeMode recommendedMode, boolean flagAvailable) {
        Set<ChallengeMode> orderedModes = new LinkedHashSet<>();
        orderedModes.add(recommendedMode);

        if (flagAvailable) {
            orderedModes.add(ChallengeMode.JIGSAW);
        }

        orderedModes.add(ChallengeMode.HISTORY);
        orderedModes.add(ChallengeMode.MATH);
        orderedModes.add(ChallengeMode.LOGIC);

        return orderedModes.stream().limit(3).toList();
    }

    private ChallengeMode resolveSelectedMode(String preferredMode, ChallengeMode recommendedMode, List<ChallengeMode> availableModes) {
        if (!StringUtils.hasText(preferredMode)) {
            return recommendedMode;
        }

        String normalizedPreferredMode = preferredMode.trim().toLowerCase(Locale.ROOT);
        return availableModes.stream()
                .filter(mode -> mode.key().equals(normalizedPreferredMode))
                .findFirst()
                .orElse(recommendedMode);
    }

    private String buildHelperText(ChallengeMode selectedMode, GeneratedChallenge generatedChallenge) {
        return switch (selectedMode) {
            case JIGSAW -> "Select one flag piece, then another, to swap them until the full flag is reconstructed.";
            case HISTORY -> "Text-first mode doubles as the accessibility fallback. Choose the historical fact that is correct.";
            case MATH -> "Choose the correct answer. Numeric mode is the fastest path for many users.";
            case LOGIC -> "Find the pattern and choose the best answer. Logic mode avoids image-heavy interaction.";
        };
    }

    private String buildProfileHint(Integer age, String countryKey) {
        if (age != null && StringUtils.hasText(countryKey)) {
            return "Profile hint: age " + age + ", country " + titleCaseCountry(countryKey) + ".";
        }
        if (age != null) {
            return "Profile hint: age " + age + ". Add your country to unlock the country flag jigsaw.";
        }
        if (StringUtils.hasText(countryKey)) {
            return "Profile hint: country " + titleCaseCountry(countryKey) + ". Add your age for a more tailored starting challenge.";
        }
        return "Choose both age and country before starting the personalized security check.";
    }

    private String buildRecommendationReason(ChallengeMode recommendedMode, Integer age, String countryKey, boolean flagAvailable) {
        return switch (recommendedMode) {
            case HISTORY -> "We started with history trivia because text-first challenges are lower-friction for this profile, while still keeping the challenge strong.";
            case MATH -> flagAvailable
                    ? "We started with quick math because younger profiles often clear numeric checks fastest. You can still switch to the flag jigsaw or history trivia."
                    : "We started with quick math because younger profiles often clear numeric checks fastest.";
            case JIGSAW -> "We started with the " + titleCaseCountry(countryKey) + " flag jigsaw because your selected country supports the visual puzzle mode.";
            case LOGIC -> "We started with a logic challenge because no country-specific flag puzzle is available for this country yet, and logic remains keyboard-friendly.";
        };
    }

    private void cleanupExpiredChallenges() {
        Instant now = Instant.now();
        storedChallenges.values().removeIf(challenge -> challenge.expiresAt().isBefore(now));
    }

    private Integer sanitizeAge(Integer age) {
        if (age == null) {
            return null;
        }
        return (age >= 6 && age <= 120) ? age : null;
    }

    private String normalizeCountryKey(String country) {
        if (!StringUtils.hasText(country)) {
            return "";
        }

        String normalized = country.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z ]", " ").replaceAll("\\s+", " ").trim();
        return switch (normalized) {
            case "USA", "US", "UNITED STATES OF AMERICA" -> "UNITED STATES";
            case "UK", "UNITED KINGDOM OF GREAT BRITAIN", "GREAT BRITAIN", "BRITAIN", "ENGLAND" -> "UNITED KINGDOM";
            default -> normalized;
        };
    }

    private String titleCaseCountry(String countryKey) {
        if (!StringUtils.hasText(countryKey)) {
            return "";
        }

        String[] parts = countryKey.toLowerCase(Locale.ROOT).split("\\s+");
        List<String> titledParts = new ArrayList<>();
        for (String part : parts) {
            titledParts.add(part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1));
        }
        return String.join(" ", titledParts);
    }

    private FlagDefinition resolveFlagDefinition(String countryKey) {
        return switch (countryKey) {
            case "FRANCE" -> new FlagDefinition("France", toDataUrl("""
                    <svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 300 200'>
                      <rect width='100' height='200' x='0' fill='#0055A4'/>
                      <rect width='100' height='200' x='100' fill='#FFFFFF'/>
                      <rect width='100' height='200' x='200' fill='#EF4135'/>
                    </svg>
                    """));
            case "TUNISIA" -> new FlagDefinition("Tunisia", toDataUrl("""
                    <svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 300 200'>
                      <rect width='300' height='200' fill='#E70013'/>
                      <circle cx='150' cy='100' r='52' fill='#FFFFFF'/>
                      <circle cx='162' cy='100' r='30' fill='#E70013'/>
                      <circle cx='170' cy='100' r='24' fill='#FFFFFF'/>
                      <polygon points='178,82 183,95 197,95 186,103 191,117 178,108 166,117 171,103 160,95 174,95' fill='#E70013'/>
                    </svg>
                    """));
            case "NIGERIA" -> new FlagDefinition("Nigeria", toDataUrl("""
                    <svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 300 200'>
                      <rect width='100' height='200' x='0' fill='#008751'/>
                      <rect width='100' height='200' x='100' fill='#FFFFFF'/>
                      <rect width='100' height='200' x='200' fill='#008751'/>
                    </svg>
                    """));
            case "GERMANY" -> new FlagDefinition("Germany", toDataUrl("""
                    <svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 300 200'>
                      <rect width='300' height='66.67' y='0' fill='#000000'/>
                      <rect width='300' height='66.67' y='66.67' fill='#DD0000'/>
                      <rect width='300' height='66.67' y='133.34' fill='#FFCE00'/>
                    </svg>
                    """));
            case "ITALY" -> new FlagDefinition("Italy", toDataUrl("""
                    <svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 300 200'>
                      <rect width='100' height='200' x='0' fill='#009246'/>
                      <rect width='100' height='200' x='100' fill='#FFFFFF'/>
                      <rect width='100' height='200' x='200' fill='#CE2B37'/>
                    </svg>
                    """));
            case "UNITED STATES" -> new FlagDefinition("United States", toDataUrl("""
                    <svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 300 200'>
                      <rect width='300' height='200' fill='#B22234'/>
                      <g fill='#FFFFFF'>
                        <rect y='15.38' width='300' height='15.38'/>
                        <rect y='46.14' width='300' height='15.38'/>
                        <rect y='76.90' width='300' height='15.38'/>
                        <rect y='107.66' width='300' height='15.38'/>
                        <rect y='138.42' width='300' height='15.38'/>
                        <rect y='169.18' width='300' height='15.38'/>
                      </g>
                      <rect width='120' height='107.7' fill='#3C3B6E'/>
                      <g fill='#FFFFFF'>
                        <circle cx='20' cy='18' r='4'/>
                        <circle cx='50' cy='18' r='4'/>
                        <circle cx='80' cy='18' r='4'/>
                        <circle cx='110' cy='18' r='4'/>
                        <circle cx='35' cy='36' r='4'/>
                        <circle cx='65' cy='36' r='4'/>
                        <circle cx='95' cy='36' r='4'/>
                        <circle cx='20' cy='54' r='4'/>
                        <circle cx='50' cy='54' r='4'/>
                        <circle cx='80' cy='54' r='4'/>
                        <circle cx='110' cy='54' r='4'/>
                        <circle cx='35' cy='72' r='4'/>
                        <circle cx='65' cy='72' r='4'/>
                        <circle cx='95' cy='72' r='4'/>
                      </g>
                    </svg>
                    """));
            case "MOROCCO" -> new FlagDefinition("Morocco", toDataUrl("""
                    <svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 300 200'>
                      <rect width='300' height='200' fill='#C1272D'/>
                      <polygon points='150,55 161,87 195,87 168,107 178,140 150,120 122,140 132,107 105,87 139,87' fill='none' stroke='#006233' stroke-width='8'/>
                    </svg>
                    """));
            case "EGYPT" -> new FlagDefinition("Egypt", toDataUrl("""
                    <svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 300 200'>
                      <rect width='300' height='66.67' y='0' fill='#CE1126'/>
                      <rect width='300' height='66.67' y='66.67' fill='#FFFFFF'/>
                      <rect width='300' height='66.67' y='133.34' fill='#000000'/>
                      <rect x='140' y='78' width='20' height='44' rx='4' fill='#CDA434'/>
                    </svg>
                    """));
            case "SPAIN" -> new FlagDefinition("Spain", toDataUrl("""
                    <svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 300 200'>
                      <rect width='300' height='50' y='0' fill='#AA151B'/>
                      <rect width='300' height='100' y='50' fill='#F1BF00'/>
                      <rect width='300' height='50' y='150' fill='#AA151B'/>
                    </svg>
                    """));
            case "ALGERIA" -> new FlagDefinition("Algeria", toDataUrl("""
                    <svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 300 200'>
                      <rect width='150' height='200' x='0' fill='#006233'/>
                      <rect width='150' height='200' x='150' fill='#FFFFFF'/>
                      <circle cx='160' cy='100' r='38' fill='#D21034'/>
                      <circle cx='170' cy='100' r='32' fill='#FFFFFF'/>
                      <polygon points='182,78 187,91 201,91 190,99 195,113 182,104 170,113 175,99 164,91 178,91' fill='#D21034'/>
                    </svg>
                    """));
            default -> null;
        };
    }

    private boolean isSolvedOrder(List<Integer> displayOrder) {
        for (int index = 0; index < displayOrder.size(); index++) {
            if (displayOrder.get(index) != index) {
                return false;
            }
        }
        return true;
    }

    private String toDataUrl(String svg) {
        String base64 = Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
        return "data:image/svg+xml;base64," + base64;
    }

    private record StoredChallenge(
            String mode,
            String expectedAnswer,
            Instant expiresAt
    ) {
    }

    private record GeneratedChallenge(
            String kind,
            String prompt,
            List<ChallengeChoice> choices,
            SignUpChallengeResponseDto.FlagJigsawDto flagJigsaw,
            String expectedAnswer
    ) {
    }

    private record ChallengeChoice(
            String id,
            String label
    ) {
    }

    private record MathQuestion(
            String prompt,
            List<String> answers,
            String correctAnswer
    ) {
    }

    private record LogicQuestion(
            String prompt,
            List<String> answers,
            String correctAnswer
    ) {
    }

    private record HistoryQuestion(
            String prompt,
            List<String> answers,
            String correctAnswer
    ) {
    }

    private record FlagDefinition(
            String countryName,
            String flagDataUrl
    ) {
    }

    private enum ChallengeMode {
        JIGSAW("jigsaw", "Flag jigsaw", "Rebuild your selected country's flag by swapping six pieces."),
        HISTORY("history", "History trivia", "Answer a country-aware or world-history multiple choice question."),
        MATH("math", "Quick math", "Solve a short arithmetic check with four choices."),
        LOGIC("logic", "Logic puzzle", "Clear a pattern or reasoning challenge without image-heavy steps.");

        private final String key;
        private final String label;
        private final String description;

        ChallengeMode(String key, String label, String description) {
            this.key = key;
            this.label = label;
            this.description = description;
        }

        public String key() {
            return key;
        }

        public String label() {
            return label;
        }

        public String description() {
            return description;
        }
    }

    private static final List<HistoryQuestion> GENERAL_HISTORY_BANK = List.of(
            new HistoryQuestion(
                    "Which year did the first human land on the Moon?",
                    List.of("1969", "1959", "1975", "1981"),
                    "1969"
            ),
            new HistoryQuestion(
                    "Which ancient civilization built Machu Picchu?",
                    List.of("Inca", "Roman", "Greek", "Phoenician"),
                    "Inca"
            ),
            new HistoryQuestion(
                    "Which empire was ruled by Julius Caesar?",
                    List.of("Roman", "Ottoman", "Mongol", "Persian"),
                    "Roman"
            )
    );

    private static final Map<String, List<HistoryQuestion>> HISTORY_BANK = Map.of(
            "FRANCE", List.of(
                    new HistoryQuestion(
                            "In which year did the French Revolution begin?",
                            List.of("1789", "1815", "1776", "1848"),
                            "1789"
                    ),
                    new HistoryQuestion(
                            "Which structure opened in Paris for the 1889 World's Fair?",
                            List.of("Eiffel Tower", "Arc de Triomphe", "Louvre Pyramid", "Palace of Versailles"),
                            "Eiffel Tower"
                    )
            ),
            "TUNISIA", List.of(
                    new HistoryQuestion(
                            "Tunisia gained independence from France in which year?",
                            List.of("1956", "1945", "1962", "1971"),
                            "1956"
                    ),
                    new HistoryQuestion(
                            "Which ancient city near modern Tunisia was a major rival of Rome?",
                            List.of("Carthage", "Athens", "Alexandria", "Sparta"),
                            "Carthage"
                    )
            ),
            "UNITED STATES", List.of(
                    new HistoryQuestion(
                            "Which document was adopted in 1776 in the United States?",
                            List.of("Declaration of Independence", "Bill of Rights", "Emancipation Proclamation", "Mayflower Compact"),
                            "Declaration of Independence"
                    ),
                    new HistoryQuestion(
                            "Which year marks the fall of the Berlin Wall, a major event in modern U.S. foreign policy history?",
                            List.of("1989", "1979", "1995", "1969"),
                            "1989"
                    )
            ),
            "NIGERIA", List.of(
                    new HistoryQuestion(
                            "Nigeria became independent in which year?",
                            List.of("1960", "1950", "1975", "1983"),
                            "1960"
                    ),
                    new HistoryQuestion(
                            "Which river joins with the Benue at Lokoja in Nigeria?",
                            List.of("Niger", "Congo", "Volta", "Zambezi"),
                            "Niger"
                    )
            ),
            "GERMANY", List.of(
                    new HistoryQuestion(
                            "In which year did the Berlin Wall fall?",
                            List.of("1989", "1979", "1991", "1961"),
                            "1989"
                    ),
                    new HistoryQuestion(
                            "Germany was formally reunified in which year?",
                            List.of("1990", "1985", "1995", "1970"),
                            "1990"
                    )
            ),
            "ITALY", List.of(
                    new HistoryQuestion(
                            "Modern Italy was unified in which year?",
                            List.of("1861", "1789", "1918", "1946"),
                            "1861"
                    ),
                    new HistoryQuestion(
                            "Which ancient empire had Rome as its capital?",
                            List.of("Roman Empire", "Byzantine Empire", "Persian Empire", "Macedonian Empire"),
                            "Roman Empire"
                    )
            ),
            "MOROCCO", List.of(
                    new HistoryQuestion(
                            "Morocco regained independence in which year?",
                            List.of("1956", "1948", "1967", "1973"),
                            "1956"
                    ),
                    new HistoryQuestion(
                            "Which city served as one of Morocco's imperial capitals?",
                            List.of("Marrakesh", "Tangier", "Agadir", "Nador"),
                            "Marrakesh"
                    )
            ),
            "EGYPT", List.of(
                    new HistoryQuestion(
                            "The Suez Canal opened in which year?",
                            List.of("1869", "1829", "1901", "1952"),
                            "1869"
                    ),
                    new HistoryQuestion(
                            "Which ancient structures are located at Giza in Egypt?",
                            List.of("Pyramids", "Colosseum", "Stonehenge", "Acropolis"),
                            "Pyramids"
                    )
            ),
            "SPAIN", List.of(
                    new HistoryQuestion(
                            "Spain's current democratic constitution was approved in which year?",
                            List.of("1978", "1931", "1965", "1992"),
                            "1978"
                    ),
                    new HistoryQuestion(
                            "Which kingdom joined with Castile to help form modern Spain?",
                            List.of("Aragon", "Bavaria", "Saxony", "Burgundy"),
                            "Aragon"
                    )
            ),
            "ALGERIA", List.of(
                    new HistoryQuestion(
                            "Algeria gained independence from France in which year?",
                            List.of("1962", "1956", "1970", "1948"),
                            "1962"
                    ),
                    new HistoryQuestion(
                            "Which city is Algeria's capital?",
                            List.of("Algiers", "Oran", "Constantine", "Annaba"),
                            "Algiers"
                    )
            )
    );
}
