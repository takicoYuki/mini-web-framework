package io.github.takicoyuki.miniweb.web;

import java.lang.reflect.RecordComponent;
import java.util.Collection;
import java.util.Map;

/**
 * コントローラーの戻り値を JSON 文字列にする最小限のシリアライザ。
 * 対応する型：null / 文字列 / 数値 / 真偽値 / Map / Collection / 配列 / record / enum
 */
public final class JsonWriter {

    private JsonWriter() {
    }

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        append(sb, value);
        return sb.toString();
    }

    private static void append(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof CharSequence || value instanceof Character || value instanceof Enum<?>) {
            appendString(sb, value.toString());
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                appendString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                append(sb, e.getValue());
                first = false;
            }
            sb.append('}');
        } else if (value instanceof Collection<?> list) {
            appendArray(sb, list.toArray());
        } else if (value instanceof Object[] array) {
            appendArray(sb, array);
        } else if (value.getClass().isRecord()) {
            sb.append('{');
            RecordComponent[] components = value.getClass().getRecordComponents();
            for (int i = 0; i < components.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                appendString(sb, components[i].getName());
                sb.append(':');
                try {
                    var accessor = components[i].getAccessor();
                    accessor.setAccessible(true);
                    append(sb, accessor.invoke(value));
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
            sb.append('}');
        } else {
            throw new IllegalArgumentException("JSON に変換できない型です: " + value.getClass().getName());
        }
    }

    private static void appendArray(StringBuilder sb, Object[] items) {
        sb.append('[');
        for (int i = 0; i < items.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            append(sb, items[i]);
        }
        sb.append(']');
    }

    private static void appendString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
