package br.senai.fatesg.soa.common;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON mínimo, sem bibliotecas externas: transforma Map/List/String/Number/Boolean
 * em texto JSON e vice-versa. É o formato "canônico" que circula no barramento.
 */
public final class Json {

    private Json() {
    }

    /** Monta um objeto JSON a partir de pares chave, valor, chave, valor... */
    public static Map<String, Object> obj(Object... keyValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            m.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return m;
    }

    // ------------------------------------------------------------------ leitura de campos

    public static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : String.valueOf(v);
    }

    public static double num(Map<String, Object> m, String key, double fallback) {
        Object v = m.get(key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s.trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }

    public static boolean bool(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof Boolean b ? b : "true".equalsIgnoreCase(String.valueOf(v));
    }

    // ------------------------------------------------------------------ escrita

    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            writeString(sb, s);
        } else if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                sb.append("null");
            } else if (d == Math.rint(d) && Math.abs(d) < 1e15) {
                sb.append((long) d);
            } else {
                sb.append(d);
            }
        } else if (v instanceof Number || v instanceof Boolean) {
            sb.append(v);
        } else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            Iterator<? extends Map.Entry<?, ?>> it = m.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<?, ?> e = it.next();
                writeString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue());
                if (it.hasNext()) {
                    sb.append(',');
                }
            }
            sb.append('}');
        } else if (v instanceof Iterable<?> list) {
            sb.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) {
                    sb.append(',');
                }
                write(sb, item);
                first = false;
            }
            sb.append(']');
        } else {
            writeString(sb, v.toString());
        }
    }

    private static void writeString(StringBuilder sb, String s) {
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

    // ------------------------------------------------------------------ leitura (parser)

    /** Lê um texto JSON que deve ser um objeto { ... }. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object v = parse(text);
        if (!(v instanceof Map)) {
            throw new IllegalArgumentException("JSON não é um objeto");
        }
        return (Map<String, Object>) v;
    }

    public static Object parse(String text) {
        Parser p = new Parser(text == null ? "" : text);
        p.skipWs();
        Object v = p.value();
        p.skipWs();
        if (p.i != p.s.length()) {
            throw p.error("conteúdo sobrando");
        }
        return v;
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        Object value() {
            if (i >= s.length()) {
                throw error("fim inesperado");
            }
            char c = s.charAt(i);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; // {
            skipWs();
            if (peek() == '}') {
                i++;
                return m;
            }
            while (true) {
                skipWs();
                if (peek() != '"') {
                    throw error("esperava nome do campo");
                }
                String key = string();
                skipWs();
                expect(':');
                skipWs();
                m.put(key, value());
                skipWs();
                char c = next();
                if (c == '}') {
                    return m;
                }
                if (c != ',') {
                    throw error("esperava , ou }");
                }
            }
        }

        List<Object> array() {
            List<Object> list = new ArrayList<>();
            i++; // [
            skipWs();
            if (peek() == ']') {
                i++;
                return list;
            }
            while (true) {
                skipWs();
                list.add(value());
                skipWs();
                char c = next();
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    throw error("esperava , ou ]");
                }
            }
        }

        String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char e = next();
                switch (e) {
                    case '"', '\\', '/' -> sb.append(e);
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (i + 4 > s.length()) {
                            throw error("escape \\u incompleto");
                        }
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> throw error("escape inválido");
                }
            }
        }

        Object number() {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
                i++;
            }
            String n = s.substring(start, i);
            if (n.isEmpty()) {
                throw error("valor inválido");
            }
            try {
                if (n.contains(".") || n.contains("e") || n.contains("E")) {
                    return Double.parseDouble(n);
                }
                return Long.parseLong(n);
            } catch (NumberFormatException e) {
                throw error("número inválido: " + n);
            }
        }

        Object literal(String word, Object result) {
            if (!s.startsWith(word, i)) {
                throw error("valor inválido");
            }
            i += word.length();
            return result;
        }

        void skipWs() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        char peek() {
            return i < s.length() ? s.charAt(i) : '\0';
        }

        char next() {
            if (i >= s.length()) {
                throw error("fim inesperado");
            }
            return s.charAt(i++);
        }

        void expect(char c) {
            if (next() != c) {
                throw error("esperava '" + c + "'");
            }
        }

        IllegalArgumentException error(String msg) {
            return new IllegalArgumentException("JSON inválido (posição " + i + "): " + msg);
        }
    }
}
