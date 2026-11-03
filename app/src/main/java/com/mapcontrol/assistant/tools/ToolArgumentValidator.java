package com.mapcontrol.assistant.tools;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Modelin ürettiği argümanları doğrular. Hatalı/eksik/uydurulmuş argümanda
 * yürütme yapılmaz; çağıran tarafa Türkçe hata döner.
 */
public final class ToolArgumentValidator {

    public static final class Result {
        public final boolean valid;
        public final String error;
        public final Map<String, String> values;

        private Result(boolean valid, String error, Map<String, String> values) {
            this.valid = valid;
            this.error = error;
            this.values = values;
        }

        public static Result error(String message) {
            return new Result(false, message, new LinkedHashMap<>());
        }

        public static Result ok(Map<String, String> values) {
            return new Result(true, "", values);
        }
    }

    private static final int MAX_VALUE_LENGTH = 200;

    private ToolArgumentValidator() {
    }

    public static Result validate(ToolSpec spec, Map<String, Object> raw) {
        if (spec == null) {
            return Result.error("Bilinmeyen işlem.");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (ToolSpec.Param param : spec.params) {
            Object value = raw == null ? null : raw.get(param.name);
            if (value == null) {
                if (param.required) {
                    return Result.error("\"" + param.name + "\" alanı gerekli.");
                }
                continue;
            }
            String text = coerce(value);
            if (text.length() > MAX_VALUE_LENGTH) {
                return Result.error("\"" + param.name + "\" çok uzun.");
            }
            if (ToolSpec.TYPE_INTEGER.equals(param.type)) {
                int number;
                try {
                    number = Integer.parseInt(text);
                } catch (NumberFormatException e) {
                    return Result.error("\"" + param.name + "\" sayı olmalı.");
                }
                if (param.hasRange() && (number < param.min || number > param.max)) {
                    return Result.error("\"" + param.name + "\" " + param.min
                            + " ile " + param.max + " arasında olmalı.");
                }
                text = String.valueOf(number);
            } else if (ToolSpec.TYPE_BOOLEAN.equals(param.type)) {
                Boolean bool = parseBoolean(text);
                if (bool == null) {
                    return Result.error("\"" + param.name + "\" açık/kapalı olmalı.");
                }
                text = bool ? "true" : "false";
            } else if (param.enumValues != null && param.enumValues.length > 0) {
                String canonical = canonicalEnum(param.enumValues, text);
                if (canonical == null) {
                    return Result.error("\"" + param.name + "\" geçersiz değer: " + text);
                }
                text = canonical;
            }
            values.put(param.name, text);
        }
        for (ToolSpec.Param param : spec.params) {
            if (!param.required && !values.containsKey(param.name)) {
                values.remove(param.name);
            }
        }
        return Result.ok(values);
    }

    private static String coerce(Object value) {
        if (value instanceof Boolean) {
            return ((Boolean) value) ? "true" : "false";
        }
        if (value instanceof Number) {
            double d = ((Number) value).doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d)) {
                return String.valueOf((long) d);
            }
            return String.valueOf(d);
        }
        return String.valueOf(value).trim();
    }

    private static Boolean parseBoolean(String text) {
        String value = text.trim().toLowerCase(java.util.Locale.US);
        if ("true".equals(value) || "1".equals(value) || "on".equals(value)
                || "aç".equals(value) || "ac".equals(value) || "açık".equals(value)
                || "evet".equals(value)) {
            return Boolean.TRUE;
        }
        if ("false".equals(value) || "0".equals(value) || "off".equals(value)
                || "kapat".equals(value) || "kapalı".equals(value) || "hayır".equals(value)
                || "hayir".equals(value)) {
            return Boolean.FALSE;
        }
        return null;
    }

    /** Enum karşılaştırması: Türkçe küçük harf + alt çizgi/boşluk toleransı. */
    private static String canonicalEnum(String[] values, String text) {
        String normalized = normalize(text);
        for (String candidate : values) {
            if (normalize(candidate).equals(normalized)) {
                return candidate;
            }
        }
        return null;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(new java.util.Locale("tr", "TR"))
                .replace('_', ' ').replace('-', ' ').replaceAll("\\s+", " ");
    }
}
