package com.polytech.knowledgebase.structure.search;

import java.text.Normalizer;
import java.util.Locale;

public final class TextNormalizer {
    private TextNormalizer() {}

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }

        return Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replace('ё', 'е').replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }

    public static boolean containsPhrase(String normalizedText, String phrase) {
        String normalizedPhrase = normalize(phrase);
        return !normalizedPhrase.isEmpty() && (" " + normalizedText + " ").contains(" " + normalizedPhrase + " ");
    }

    public static boolean containsUnnegatedPhrase(String normalizedText, String phrase) {
        String normalizedPhrase = normalize(phrase);
        if (normalizedPhrase.isEmpty()) {
            return false;
        }

        String padded = " " + normalizedText + " ";
        String needle = " " + normalizedPhrase + " ";
        int position = padded.indexOf(needle);

        while (position >= 0) {
            String prefix = padded.substring(0, position).stripTrailing();
            if (!prefix.matches(".*(?:^| )(?:не|без)(?: для)?$")) {
                return true;
            }

            position = padded.indexOf(needle, position + 1);
        }

        return false;
    }
}
