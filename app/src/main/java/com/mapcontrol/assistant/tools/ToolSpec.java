package com.mapcontrol.assistant.tools;

import java.util.ArrayList;
import java.util.List;

/**
 * Gemini function-declaration tanımı. LLM yalnızca yüksek seviyeli enum'ları görür;
 * modül/ID/değer bilgisi hiçbir şemada yer almaz.
 */
public final class ToolSpec {

    public static final String TYPE_STRING = "string";
    public static final String TYPE_INTEGER = "integer";
    public static final String TYPE_BOOLEAN = "boolean";

    public static final class Param {
        public final String name;
        public final String type;
        public final String description;
        public final String[] enumValues;
        public final boolean required;
        /** integer aralığı; -1 yok. */
        public final int min;
        public final int max;

        public Param(String name, String type, String description, String[] enumValues,
                boolean required) {
            this(name, type, description, enumValues, required, Integer.MIN_VALUE,
                    Integer.MAX_VALUE);
        }

        public Param(String name, String type, String description, String[] enumValues,
                boolean required, int min, int max) {
            this.name = name;
            this.type = type;
            this.description = description;
            this.enumValues = enumValues;
            this.required = required;
            this.min = min;
            this.max = max;
        }

        public boolean hasRange() {
            return min != Integer.MIN_VALUE || max != Integer.MAX_VALUE;
        }
    }

    public final String name;
    public final String description;
    public final List<Param> params = new ArrayList<>();

    public ToolSpec(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public ToolSpec param(String name, String type, String description, String[] enums,
            boolean required) {
        params.add(new Param(name, type, description, enums, required));
        return this;
    }

    /** Son eklenen parametreye sayı aralığı verir. */
    public ToolSpec range(int min, int max) {
        if (!params.isEmpty()) {
            Param last = params.remove(params.size() - 1);
            params.add(new Param(last.name, last.type, last.description, last.enumValues,
                    last.required, min, max));
        }
        return this;
    }

    public Param find(String paramName) {
        for (Param param : params) {
            if (param.name.equals(paramName)) {
                return param;
            }
        }
        return null;
    }

    /** Gemini {@code parameters} şeması (OpenAPI alt kümesi). */
    public String schemaJson() {
        StringBuilder b = new StringBuilder(256);
        b.append("{\"type\":\"OBJECT\",\"properties\":{");
        boolean first = true;
        for (Param param : params) {
            if (!first) {
                b.append(',');
            }
            first = false;
            b.append('"').append(param.name).append("\":{\"type\":\"")
                    .append(param.type.toUpperCase(java.util.Locale.US)).append('"');
            if (param.description != null && !param.description.isEmpty()) {
                b.append(",\"description\":\"").append(escape(param.description)).append('"');
            }
            if (param.enumValues != null && param.enumValues.length > 0) {
                b.append(",\"enum\":[");
                for (int i = 0; i < param.enumValues.length; i++) {
                    if (i > 0) {
                        b.append(',');
                    }
                    b.append('"').append(escape(param.enumValues[i])).append('"');
                }
                b.append(']');
            }
            b.append('}');
        }
        b.append('}');
        List<String> required = new ArrayList<>();
        for (Param param : params) {
            if (param.required) {
                required.add(param.name);
            }
        }
        if (!required.isEmpty()) {
            b.append(",\"required\":[");
            for (int i = 0; i < required.size(); i++) {
                if (i > 0) {
                    b.append(',');
                }
                b.append('"').append(required.get(i)).append('"');
            }
            b.append(']');
        }
        b.append('}');
        return b.toString();
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                b.append('\\');
            }
            if (c == '\n') {
                b.append("\\n");
                continue;
            }
            b.append(c);
        }
        return b.toString();
    }
}
