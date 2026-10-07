package com.iulianlounge.backend.service;

import java.util.regex.Pattern;

final class TalkText {

    static final int MAX_LENGTH = 400;

    private static final Pattern CONTROL = Pattern.compile("[\\p{Cc}&&[^\\s]]");
    private static final Pattern SEPARATORS = Pattern.compile("[\\s\\p{Z}]+");
    private static final String SENTENCE_ENDS = ".!?…";
    private static final String ELLIPSIS = "…";

    private TalkText() {
    }

    static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String withoutControl = CONTROL.matcher(raw).replaceAll("");
        String collapsed = SEPARATORS.matcher(withoutControl).replaceAll(" ").strip();
        return collapsed.length() <= MAX_LENGTH ? collapsed : cut(collapsed);
    }

    private static String cut(String text) {
        String window = text.substring(0, MAX_LENGTH);
        int sentenceEnd = lastSentenceEnd(window);
        if (sentenceEnd >= 0) {
            return window.substring(0, sentenceEnd + 1);
        }
        String room = withoutHalfPair(text.substring(0, MAX_LENGTH - ELLIPSIS.length()));
        int lastSpace = room.lastIndexOf(' ');
        return (lastSpace > 0 ? room.substring(0, lastSpace) : room) + ELLIPSIS;
    }

    private static int lastSentenceEnd(String window) {
        for (int i = window.length() - 1; i >= 0; i--) {
            if (SENTENCE_ENDS.indexOf(window.charAt(i)) >= 0) {
                return i;
            }
        }
        return -1;
    }

    private static String withoutHalfPair(String text) {
        boolean endsInsideAPair = Character.isHighSurrogate(text.charAt(text.length() - 1));
        return endsInsideAPair ? text.substring(0, text.length() - 1) : text;
    }
}
