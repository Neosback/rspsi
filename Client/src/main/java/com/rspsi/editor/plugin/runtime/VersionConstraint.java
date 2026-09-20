package com.rspsi.editor.plugin.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Semantic-version constraint supporting exact versions, comparison clauses,
 * caret ranges and tilde ranges. Multiple clauses are ANDed.
 */
public final class VersionConstraint {
    public static final VersionConstraint ANY = new VersionConstraint("*", List.of());

    private final String expression;
    private final List<Clause> clauses;

    private VersionConstraint(String expression, List<Clause> clauses) {
        this.expression = expression;
        this.clauses = List.copyOf(clauses);
    }

    public static VersionConstraint parse(String value) {
        String expression = value == null ? "*" : value.trim();
        if (expression.isEmpty() || expression.equals("*")) return ANY;
        List<Clause> clauses = new ArrayList<>();
        for (String token : expression.replace(",", " ").trim().split("\\s+")) {
            if (token.isBlank()) continue;
            if (token.startsWith("^")) {
                SemanticVersion lower = SemanticVersion.parse(token.substring(1));
                clauses.add(new Clause(Operator.GTE, lower));
                clauses.add(new Clause(Operator.LT, caretUpper(lower)));
            } else if (token.startsWith("~")) {
                SemanticVersion lower = SemanticVersion.parse(token.substring(1));
                clauses.add(new Clause(Operator.GTE, lower));
                clauses.add(new Clause(Operator.LT,
                        new SemanticVersion(lower.major(), lower.minor() + 1, 0, "")));
            } else {
                String operator = token.startsWith(">=") || token.startsWith("<=")
                        ? token.substring(0, 2)
                        : token.startsWith(">") || token.startsWith("<") || token.startsWith("=")
                        ? token.substring(0, 1) : "=";
                String version = operator.equals("=") && !token.startsWith("=")
                        ? token : token.substring(operator.length());
                clauses.add(new Clause(Operator.from(operator), SemanticVersion.parse(version)));
            }
        }
        return new VersionConstraint(expression, clauses);
    }

    public boolean matches(SemanticVersion version) {
        Objects.requireNonNull(version, "version");
        for (Clause clause : clauses) {
            int comparison = version.compareTo(clause.version());
            boolean matches = switch (clause.operator()) {
                case EQ -> comparison == 0;
                case GT -> comparison > 0;
                case GTE -> comparison >= 0;
                case LT -> comparison < 0;
                case LTE -> comparison <= 0;
            };
            if (!matches) return false;
        }
        return true;
    }

    public String expression() {
        return expression;
    }

    private static SemanticVersion caretUpper(SemanticVersion lower) {
        if (lower.major() > 0) return new SemanticVersion(lower.major() + 1, 0, 0, "");
        if (lower.minor() > 0) return new SemanticVersion(0, lower.minor() + 1, 0, "");
        return new SemanticVersion(0, 0, lower.patch() + 1, "");
    }

    private enum Operator {
        EQ, GT, GTE, LT, LTE;
        private static Operator from(String value) {
            return switch (value) {
                case ">" -> GT;
                case ">=" -> GTE;
                case "<" -> LT;
                case "<=" -> LTE;
                default -> EQ;
            };
        }
    }

    private record Clause(Operator operator, SemanticVersion version) { }

    @Override
    public String toString() {
        return expression;
    }
}
