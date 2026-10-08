package com.safedetect.agent;

import java.util.Locale;

/** Small in-game calculator. */
final class CalcPlugin extends PluginPack.Base {
    CalcPlugin() {
        super("calc", "Calc", "Evaluates + - * / and brackets. x is multiply.", "", "/sd calc <expr>");
    }

    @Override
    public boolean command(String[] parts) {
        if (parts.length < 3) {
            api.chat("\u00a7cUsage: /sd calc 4+4*2");
            return true;
        }
        StringBuilder expr = new StringBuilder();
        for (int i = 2; i < parts.length; i++) {
            expr.append(parts[i]);
        }
        try {
            double value = eval(expr.toString());
            String shown = Math.abs(value - Math.rint(value)) < 1e-9
                    ? String.valueOf((long) Math.rint(value))
                    : String.format(Locale.US, "%.8f", Double.valueOf(value)).replaceAll("0+$", "").replaceAll("\\.$",
                            "");
            api.chat("\u00a77CALC \u00a7f" + expr + " \u00a78= \u00a7a" + shown);
        } catch (IllegalArgumentException failed) {
            api.chat("\u00a7cInvalid expression.");
        }
        return true;
    }

    static double eval(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("empty");
        }
        String expr = raw.replace(" ", "").replace("x", "*").replace("X", "*").replace("\u00d7", "*").replace("\u00f7",
                "/");
        if (expr.isEmpty()) {
            throw new IllegalArgumentException("empty");
        }
        Parser parser = new Parser(expr);
        double value = parser.expression();
        parser.end();
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("nan");
        }
        return value;
    }

    private static final class Parser {
        private final String text;
        private int i;

        Parser(String text) {
            this.text = text;
        }

        double expression() {
            double value = term();
            while (true) {
                if (eat('+')) {
                    value += term();
                } else if (eat('-')) {
                    value -= term();
                } else {
                    return value;
                }
            }
        }

        private double term() {
            double value = factor();
            while (true) {
                if (eat('*')) {
                    value *= factor();
                } else if (eat('/')) {
                    double div = factor();
                    if (div == 0.0) {
                        throw new IllegalArgumentException("div0");
                    }
                    value /= div;
                } else {
                    return value;
                }
            }
        }

        private double factor() {
            if (eat('+')) {
                return factor();
            }
            if (eat('-')) {
                return -factor();
            }
            if (eat('(')) {
                double value = expression();
                if (!eat(')')) {
                    throw new IllegalArgumentException("paren");
                }
                return value;
            }
            int start = i;
            while (i < text.length() && (Character.isDigit(text.charAt(i)) || text.charAt(i) == '.')) {
                i++;
            }
            if (start == i) {
                throw new IllegalArgumentException("num");
            }
            try {
                return Double.parseDouble(text.substring(start, i));
            } catch (NumberFormatException failed) {
                throw new IllegalArgumentException("num");
            }
        }

        private boolean eat(char c) {
            if (i < text.length() && text.charAt(i) == c) {
                i++;
                return true;
            }
            return false;
        }

        void end() {
            if (i != text.length()) {
                throw new IllegalArgumentException("tail");
            }
        }
    }
}
