package com.mapcontrol.assistant.tools;

/** Araç yürütme sonucu: {@code {ok, message, state}} sözleşmesi. */
public final class ToolResult {

    public final boolean ok;
    public final String message;
    /** Kısa JSON nesnesi (durum) veya boş. */
    public final String state;

    public ToolResult(boolean ok, String message, String state) {
        this.ok = ok;
        this.message = message == null ? "" : message;
        this.state = state == null ? "" : state;
    }

    public static ToolResult ok(String message) {
        return new ToolResult(true, message, "");
    }

    public static ToolResult ok(String message, String stateJson) {
        return new ToolResult(true, message, stateJson);
    }

    public static ToolResult fail(String message) {
        return new ToolResult(false, message, "");
    }

    /** {@code functionResponse.response} gövdesi. */
    public String responseJson() {
        StringBuilder b = new StringBuilder(96);
        b.append("{\"ok\":").append(ok).append(",\"message\":\"")
                .append(escape(message)).append('"');
        if (state != null && !state.isEmpty()) {
            b.append(",\"state\":").append(state);
        }
        b.append('}');
        return b.toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(value.length() + 8);
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
                        b.append(' ');
                    } else {
                        b.append(c);
                    }
            }
        }
        return b.toString();
    }
}
