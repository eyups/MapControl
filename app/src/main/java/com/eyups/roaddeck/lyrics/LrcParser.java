package com.eyups.roaddeck.lyrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LRC ayrıştırıcı. Standart [mm:ss.xx] satır zamanlarını ve enhanced LRC
 * &lt;mm:ss.xx&gt; kelime zamanlarını destekler.
 */
public final class LrcParser {

    private static final Pattern LINE_TS =
            Pattern.compile("\\[(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern WORD_TS =
            Pattern.compile("<(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?>");

    private LrcParser() {
    }

    /** Zaman damgalı sözleri ayrıştırır; sonuç zaman sırasına göre döner. */
    public static List<LyricLine> parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<LyricLine> out = new ArrayList<>();
        for (String line : raw.split("\\r?\\n")) {
            parseLine(line, out);
        }
        Collections.sort(out, new Comparator<LyricLine>() {
            @Override
            public int compare(LyricLine a, LyricLine b) {
                return Long.compare(a.timeMs, b.timeMs);
            }
        });
        return fillEndTimes(out);
    }

    /** Zaman damgası olmayan düz sözler. */
    public static List<LyricLine> parsePlain(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<LyricLine> out = new ArrayList<>();
        for (String line : raw.split("\\r?\\n")) {
            String text = line.trim();
            if (text.isEmpty()) {
                continue;
            }
            out.add(new LyricLine(-1L, -1L, text, null, true));
        }
        return out;
    }

    private static void parseLine(String line, List<LyricLine> out) {
        String rest = line == null ? "" : line.trim();
        List<Long> stamps = new ArrayList<>();
        while (rest.startsWith("[")) {
            Matcher m = LINE_TS.matcher(rest);
            if (!m.find() || m.start() != 0) {
                break;
            }
            stamps.add(timestamp(m));
            rest = rest.substring(m.end()).trim();
        }
        if (stamps.isEmpty()) {
            return;
        }
        List<LyricWord> words = parseWords(rest);
        String text = buildText(rest, words);
        if (text.isEmpty()) {
            text = "♪";
        }
        for (Long stamp : stamps) {
            out.add(new LyricLine(stamp, -1L, text, words, false));
        }
    }

    private static List<LyricWord> parseWords(String text) {
        List<LyricWord> words = new ArrayList<>();
        Matcher m = WORD_TS.matcher(text);
        int last = 0;
        long current = -1L;
        while (m.find()) {
            String segment = text.substring(last, m.start()).trim();
            if (current >= 0L && !segment.isEmpty()) {
                words.add(new LyricWord(current, segment));
            }
            current = timestamp(m);
            last = m.end();
        }
        String tail = text.substring(last).trim();
        if (current >= 0L && !tail.isEmpty()) {
            words.add(new LyricWord(current, tail));
        }
        return words.size() > 1 ? words : Collections.<LyricWord>emptyList();
    }

    private static String buildText(String rest, List<LyricWord> words) {
        if (!words.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (LyricWord word : words) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(word.text);
            }
            return sb.toString().trim();
        }
        return WORD_TS.matcher(rest).replaceAll("").trim();
    }

    private static long timestamp(Matcher m) {
        int minutes = Integer.parseInt(m.group(1));
        int seconds = Integer.parseInt(m.group(2));
        String frac = m.groupCount() >= 3 ? m.group(3) : null;
        int millis = 0;
        if (frac != null && !frac.isEmpty()) {
            millis = Integer.parseInt(frac);
            if (frac.length() == 1) {
                millis *= 100;
            } else if (frac.length() == 2) {
                millis *= 10;
            }
        }
        return minutes * 60000L + seconds * 1000L + millis;
    }

    private static List<LyricLine> fillEndTimes(List<LyricLine> lines) {
        if (lines.isEmpty()) {
            return lines;
        }
        List<LyricLine> fixed = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            LyricLine line = lines.get(i);
            long end = i + 1 < lines.size() ? lines.get(i + 1).timeMs : line.timeMs + 6000L;
            if (end <= line.timeMs || end - line.timeMs > 15000L) {
                end = line.timeMs + 6000L;
            }
            fixed.add(new LyricLine(line.timeMs, end, line.text, line.words, line.plain));
        }
        return fixed;
    }
}
