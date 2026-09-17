package com.cappleapple.mastery.progression;

/** Small arithmetic language for cumulative point awards. No scripting or reflection is executed. */
public final class PointFormula {
    private PointFormula() {}

    /** Supports level, + - * / %, parentheses, floor/ceil/round/abs, min/max and pow. */
    public static double evaluate(String expression, int level) {
        if (expression == null || expression.isBlank()) return 0;
        if (expression.length() > 1024) throw new IllegalArgumentException("point_formula exceeds 1024 characters");
        Parser parser = new Parser(expression, level);
        double value = parser.expression();
        parser.whitespace();
        if (parser.position != expression.length()) throw parser.error("unexpected token");
        if (!Double.isFinite(value)) throw parser.error("non-finite result");
        return value;
    }

    public static int total(String expression, int level) {
        double value = evaluate(expression, level);
        if (value < 0 || value > Integer.MAX_VALUE) throw new IllegalArgumentException("point_formula total must be between 0 and 2147483647");
        return (int) Math.floor(value);
    }

    private static final class Parser {
        private final String source;
        private final int level;
        private int position;
        private int depth;
        Parser(String source, int level) { this.source = source; this.level = level; }

        double expression() {
            double result = term();
            while (true) {
                if (take('+')) result += term();
                else if (take('-')) result -= term();
                else return result;
            }
        }

        double term() {
            double result = factor();
            while (true) {
                if (take('*')) result *= factor();
                else if (take('/')) result /= factor();
                else if (take('%')) result %= factor();
                else return result;
            }
        }

        double factor() {
            if (++depth > 64) throw error("nesting limit exceeded");
            try { return atom(); }
            finally { depth--; }
        }

        double atom() {
            if (take('+')) return factor();
            if (take('-')) return -factor();
            if (take('(')) { double value = expression(); expect(')'); return value; }
            whitespace();
            int start = position;
            if (position < source.length() && Character.isLetter(source.charAt(position))) {
                while (position < source.length() && Character.isLetter(source.charAt(position))) position++;
                String name = source.substring(start, position);
                if (name.equals("level")) return level;
                expect('(');
                double first = expression();
                Double second = take(',') ? expression() : null;
                expect(')');
                return switch (name) {
                    case "floor" -> unary(name, first, second, Math.floor(first));
                    case "ceil" -> unary(name, first, second, Math.ceil(first));
                    case "round" -> unary(name, first, second, Math.floor(first + 0.5));
                    case "abs" -> unary(name, first, second, Math.abs(first));
                    case "min" -> Math.min(first, requiredSecond(name, second));
                    case "max" -> Math.max(first, requiredSecond(name, second));
                    case "pow" -> Math.pow(first, requiredSecond(name, second));
                    default -> throw error("unknown name " + name);
                };
            }
            while (position < source.length() && (Character.isDigit(source.charAt(position)) || source.charAt(position) == '.')) position++;
            if (position == start) throw error("expected a number or level");
            try { return Double.parseDouble(source.substring(start, position)); }
            catch (NumberFormatException ex) { throw error("malformed number"); }
        }

        double unary(String name, double first, Double second, double result) {
            if (second != null) throw error(name + " expects one argument");
            return result;
        }
        double requiredSecond(String name, Double second) {
            if (second == null) throw error(name + " expects two arguments");
            return second;
        }
        void whitespace() { while (position < source.length() && Character.isWhitespace(source.charAt(position))) position++; }
        boolean take(char character) {
            whitespace();
            if (position >= source.length() || source.charAt(position) != character) return false;
            position++;
            return true;
        }
        void expect(char character) { if (!take(character)) throw error("expected '" + character + "'"); }
        IllegalArgumentException error(String message) { return new IllegalArgumentException("point_formula at " + position + ": " + message); }
    }
}
