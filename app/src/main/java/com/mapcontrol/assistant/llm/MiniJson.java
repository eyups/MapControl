package com.mapcontrol.assistant.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON okuyucu/yazıcı. {@code org.json} Android stub'ında JVM unit
 * testlerinde çalışmadığı için asistan katmanı bu bağımsız çözümleyiciyi kullanır.
 */
public final class MiniJson {

    private MiniJson() {
    }

    public static Object parse(String text) {
        if (text == null) {
            return null;
        }
        Parser parser = new Parser(text);
        Object value = parser.readValue();
        parser.skipWhitespace();
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object value = parse(text);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }

    public static String stringify(Object value) {
        StringBuilder b = new StringBuilder(64);
        write(b, value);
        return b.toString();
    }

    private static void write(StringBuilder b, Object value) {
        if (value == null) {
            b.append("null");
        } else if (value instanceof String) {
            writeString(b, (String) value);
        } else if (value instanceof Boolean) {
            b.append(value);
        } else if (value instanceof Number) {
            double d = ((Number) value).doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d)) {
                b.append((long) d);
            } else {
                b.append(d);
            }
        } else if (value instanceof Map) {
            b.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!first) {
                    b.append(',');
                }
                first = false;
                writeString(b, String.valueOf(entry.getKey()));
                b.append(':');
                write(b, entry.getValue());
            }
            b.append('}');
        } else if (value instanceof Iterable) {
            b.append('[');
            boolean first = true;
            for (Object item : (Iterable<?>) value) {
                if (!first) {
                    b.append(',');
                }
                first = false;
                write(b, item);
            }
            b.append(']');
        } else {
            writeString(b, String.valueOf(value));
        }
    }

    public static void writeString(StringBuilder b, String value) {
        b.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    b.append("\\\"");
                    break;
                case '\\':
                    b.append("\\\\");
                    break;
                case '\n':
                    b.append("\\n");
                    break;
                case '\r':
                    b.append("\\r");
                    break;
                case '\t':
                    b.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        b.append(String.format(java.util.Locale.US, "\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
            }
        }
        b.append('"');
    }

    private static final class Parser {
        private final String text;
        private int index;

        Parser(String text) {
            this.text = text;
        }

        void skipWhitespace() {
            while (index < text.length()) {
                char c = text.charAt(index);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                    index++;
                } else {
                    break;
                }
            }
        }

        Object readValue() {
            skipWhitespace();
            if (index >= text.length()) {
                return null;
            }
            char c = text.charAt(index);
            switch (c) {
                case '{':
                    return readObject();
                case '[':
                    return readArray();
                case '"':
                    return readString();
                case 't':
                    return readLiteral("true", Boolean.TRUE);
                case 'f':
                    return readLiteral("false", Boolean.FALSE);
                case 'n':
                    return readLiteral("null", null);
                default:
                    return readNumber();
            }
        }

        private Map<String, Object> readObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            index++; // {
            skipWhitespace();
            if (index < text.length() && text.charAt(index) == '}') {
                index++;
                return map;
            }
            while (index < text.length()) {
                skipWhitespace();
                if (index >= text.length() || text.charAt(index) != '"') {
                    break;
                }
                String key = readString();
                skipWhitespace();
                if (index < text.length() && text.charAt(index) == ':') {
                    index++;
                }
                Object value = readValue();
                map.put(key, value);
                skipWhitespace();
                if (index < text.length() && text.charAt(index) == ',') {
                    index++;
                    continue;
                }
                if (index < text.length() && text.charAt(index) == '}') {
                    index++;
                    break;
                }
                break;
            }
            return map;
        }

        private List<Object> readArray() {
            List<Object> list = new ArrayList<>();
            index++; // [
            skipWhitespace();
            if (index < text.length() && text.charAt(index) == ']') {
                index++;
                return list;
            }
            while (index < text.length()) {
                list.add(readValue());
                skipWhitespace();
                if (index < text.length() && text.charAt(index) == ',') {
                    index++;
                    continue;
                }
                if (index < text.length() && text.charAt(index) == ']') {
                    index++;
                    break;
                }
                break;
            }
            return list;
        }

        private String readString() {
            StringBuilder b = new StringBuilder();
            index++; // opening quote
            while (index < text.length()) {
                char c = text.charAt(index++);
                if (c == '"') {
                    break;
                }
                if (c != '\\') {
                    b.append(c);
                    continue;
                }
                if (index >= text.length()) {
                    break;
                }
                char esc = text.charAt(index++);
                switch (esc) {
                    case 'n':
                        b.append('\n');
                        break;
                    case 'r':
                        b.append('\r');
                        break;
                    case 't':
                        b.append('\t');
                        break;
                    case 'b':
                        b.append('\b');
                        break;
                    case 'f':
                        b.append('\f');
                        break;
                    case 'u':
                        if (index + 4 <= text.length()) {
                            try {
                                b.append((char) Integer.parseInt(
                                        text.substring(index, index + 4), 16));
                                index += 4;
                            } catch (NumberFormatException ignored) {
                            }
                        }
                        break;
                    default:
                        b.append(esc);
                }
            }
            return b.toString();
        }

        private Object readLiteral(String literal, Object value) {
            if (text.regionMatches(index, literal, 0, literal.length())) {
                index += literal.length();
                return value;
            }
            index++;
            return null;
        }

        private Object readNumber() {
            int start = index;
            while (index < text.length()) {
                char c = text.charAt(index);
                if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.'
                        || c == 'e' || c == 'E') {
                    index++;
                } else {
                    break;
                }
            }
            String raw = text.substring(start, index);
            try {
                if (raw.indexOf('.') < 0 && raw.indexOf('e') < 0 && raw.indexOf('E') < 0) {
                    return Long.parseLong(raw);
                }
                return Double.parseDouble(raw);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
