package com.swag.tournaments.engine;

import java.util.Map;

/**
 * Lightweight recursive-descent expression evaluator.
 *
 * Grammar:
 *   expr   = term ( ('+' | '-') term )*
 *   term   = factor ( ('*' | '/') factor )*
 *   factor = NUMBER | IDENTIFIER | 'Math.max' '(' expr ',' expr ')'
 *                                | 'Math.min' '(' expr ',' expr ')'
 *                                | '(' expr ')'
 *
 * No scripting engine is used — avoids security risks and Nashorn removal in Java 15+.
 */
public final class FormulaEvaluator {

    private FormulaEvaluator() {}

    public static double evaluate(String formula, Map<String, Double> variables) {
        if (formula == null || formula.isBlank()) return 1.0;
        Parser parser = new Parser(formula.trim(), variables);
        return parser.parseExpr();
    }

    private static final class Parser {
        private final String input;
        private final Map<String, Double> vars;
        private int pos;

        Parser(String input, Map<String, Double> vars) {
            this.input = input;
            this.vars = vars;
            this.pos = 0;
        }

        private void skipWs() {
            while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) pos++;
        }

        double parseExpr() {
            double left = parseTerm();
            skipWs();
            while (pos < input.length() && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) {
                char op = input.charAt(pos++);
                double right = parseTerm();
                left = op == '+' ? left + right : left - right;
                skipWs();
            }
            return left;
        }

        private double parseTerm() {
            double left = parseFactor();
            skipWs();
            while (pos < input.length() && (input.charAt(pos) == '*' || input.charAt(pos) == '/')) {
                char op = input.charAt(pos++);
                double right = parseFactor();
                if (op == '/') {
                    left = right == 0.0 ? 0.0 : left / right;
                } else {
                    left = left * right;
                }
                skipWs();
            }
            return left;
        }

        private double parseFactor() {
            skipWs();
            if (pos >= input.length()) return 0.0;

            char c = input.charAt(pos);

            // Parenthesised sub-expression
            if (c == '(') {
                pos++;
                double val = parseExpr();
                skipWs();
                if (pos < input.length() && input.charAt(pos) == ')') pos++;
                return val;
            }

            // Numeric literal (including negatives at start)
            if (Character.isDigit(c) || (c == '-' && pos + 1 < input.length() && Character.isDigit(input.charAt(pos + 1)))) {
                return parseNumber();
            }

            // Identifier or Math.max / Math.min
            if (Character.isLetter(c) || c == '_') {
                return parseIdentifierOrCall();
            }

            return 0.0;
        }

        private double parseNumber() {
            int start = pos;
            if (pos < input.length() && input.charAt(pos) == '-') pos++;
            while (pos < input.length() && (Character.isDigit(input.charAt(pos)) || input.charAt(pos) == '.')) pos++;
            try {
                return Double.parseDouble(input.substring(start, pos));
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }

        private double parseIdentifierOrCall() {
            int start = pos;
            while (pos < input.length() && (Character.isLetterOrDigit(input.charAt(pos))
                    || input.charAt(pos) == '_' || input.charAt(pos) == '.')) {
                pos++;
            }
            String name = input.substring(start, pos);
            skipWs();

            if (pos < input.length() && input.charAt(pos) == '(') {
                pos++; // consume '('
                double arg1 = parseExpr();
                skipWs();
                if (pos < input.length() && input.charAt(pos) == ',') pos++;
                double arg2 = parseExpr();
                skipWs();
                if (pos < input.length() && input.charAt(pos) == ')') pos++;

                return switch (name) {
                    case "Math.max" -> Math.max(arg1, arg2);
                    case "Math.min" -> Math.min(arg1, arg2);
                    default -> 0.0;
                };
            }

            return vars.getOrDefault(name, 0.0);
        }
    }
}
