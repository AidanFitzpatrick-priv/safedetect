package com.safedetect.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader and writer for the flags file. Gson lives in Lunar's class loader, not the agent's.
 */
final class Json {
    private final String text;
    private int pos;

    private Json(String text) {
        this.text = text;
    }

    static Object parse(String text) {
        Json json = new Json(text);
        json.skipSpace();
        Object value = json.value();
        json.skipSpace();
        if (json.pos != text.length()) {
            throw new IllegalArgumentException("Trailing data at " + json.pos);
        }
        return value;
    }

    static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20 || c == '<' || c == '>' || c == '&' || c == '=' || c == '\'') {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.append('"').toString();
    }

    private Object value() {
        if (pos >= text.length()) {
            throw new IllegalArgumentException("Unexpected end");
        }
        char c = text.charAt(pos);
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': expect("true"); return Boolean.TRUE;
            case 'f': expect("false"); return Boolean.FALSE;
            case 'n': expect("null"); return null;
            default: return number();
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        pos++;
        skipSpace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipSpace();
            String key = string();
            skipSpace();
            expect(":");
            skipSpace();
            map.put(key, value());
            skipSpace();
            char c = next();
            if (c == '}') {
                return map;
            }
            if (c != ',') {
                throw new IllegalArgumentException("Expected , or } at " + pos);
            }
        }
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<Object>();
        pos++;
        skipSpace();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipSpace();
            list.add(value());
            skipSpace();
            char c = next();
            if (c == ']') {
                return list;
            }
            if (c != ',') {
                throw new IllegalArgumentException("Expected , or ] at " + pos);
            }
        }
    }

    private String string() {
        if (next() != '"') {
            throw new IllegalArgumentException("Expected string at " + pos);
        }
        StringBuilder out = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') {
                return out.toString();
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char e = next();
            switch (e) {
                case 'n': out.append('\n'); break;
                case 'r': out.append('\r'); break;
                case 't': out.append('\t'); break;
                case 'b': out.append('\b'); break;
                case 'f': out.append('\f'); break;
                case 'u':
                    out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    pos += 4;
                    break;
                default: out.append(e);
            }
        }
    }

    private Number number() {
        int start = pos;
        while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
        String raw = text.substring(start, pos);
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("Unexpected character at " + start);
        }
        if (raw.indexOf('.') < 0 && raw.indexOf('e') < 0 && raw.indexOf('E') < 0) {
            return Long.parseLong(raw);
        }
        return Double.parseDouble(raw);
    }

    private void expect(String word) {
        if (!text.startsWith(word, pos)) {
            throw new IllegalArgumentException("Expected " + word + " at " + pos);
        }
        pos += word.length();
    }

    private char peek() {
        return pos < text.length() ? text.charAt(pos) : '\0';
    }

    private char next() {
        if (pos >= text.length()) {
            throw new IllegalArgumentException("Unexpected end");
        }
        return text.charAt(pos++);
    }

    private void skipSpace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }
}
