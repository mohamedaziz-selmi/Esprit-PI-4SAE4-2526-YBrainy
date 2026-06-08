package com.esprit.userservice.model;

public class LevelConfig {

    private static final long[] MIN_XP = {
            0, 101, 501, 1501, 5001, 15001, 40001, 100001
    };

    private static final String[] TITLES = {
            "\uD83C\uDF31 Newcomer",
            "\uD83D\uDCDA Explorer",
            "\u26A1 Contributor",
            "\uD83D\uDD25 Active Member",
            "\uD83D\uDCA1 Helper",
            "\uD83D\uDEE1\uFE0F Trusted",
            "\uD83C\uDFC5 Expert",
            "\uD83D\uDC51 Legend"
    };

    public static final int MAX_LEVEL = 8;

    public static int computeLevel(long xp) {
        int level = 1;
        for (int i = MIN_XP.length - 1; i >= 0; i--) {
            if (xp >= MIN_XP[i]) { level = i + 1; break; }
        }
        return level;
    }

    public static long minXpForLevel(int level) {
        if (level < 1) return 0;
        if (level > MAX_LEVEL) return MIN_XP[MAX_LEVEL - 1];
        return MIN_XP[level - 1];
    }

    public static long minXpForNextLevel(int currentLevel) {
        if (currentLevel >= MAX_LEVEL) return -1;
        return MIN_XP[currentLevel];
    }

    public static String getTitle(int level) {
        if (level < 1 || level > MAX_LEVEL) return TITLES[0];
        return TITLES[level - 1];
    }
}
