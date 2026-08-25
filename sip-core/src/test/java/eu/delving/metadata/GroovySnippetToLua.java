/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package eu.delving.metadata;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.builder.AstBuilder;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.GStringExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.NotExpression;
import org.codehaus.groovy.ast.expr.TernaryExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.IfStatement;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.CompilePhase;

/**
 * Translates a T1-tier Groovy mapping snippet into Lua that targets the spike
 * engine in {@code lua-poc/engine/} (Task 6's {@code node}, Task 7's
 * {@code stdlib} and {@code builder}).
 *
 * <p>Parses the snippet with the real Groovy front end at
 * {@link CompilePhase#CONVERSION} and walks the resulting AST. Anything not on
 * the T1 whitelist raises {@link UnsupportedConstructException} carrying the
 * construct's name — the converter never guesses.
 *
 * <h2>What T1 means here</h2>
 * <ul>
 *   <li>string/boolean/number constants;</li>
 *   <li>plain variable references (loop parameters, facts, {@code _uniqueIdentifier});</li>
 *   <li>GString interpolation, lowered to {@code stdlib.gs} so that
 *       empty-template suppression (spec section 3.4) still applies;</li>
 *   <li>calls to the {@link #T1_METHODS} whitelist, lowered to
 *       {@code stdlib.<name>(receiver, args...)};</li>
 *   <li>{@code _M4['key']} tuple-map subscripts, lowered to the generated
 *       {@code _tget} helper (spec section 2.4 / {@code MappingCategory.TupleMap},
 *       whose {@code get} returns {@code ""} rather than null for a miss);</li>
 *   <li>{@code ==} / {@code !=} compared by <em>text</em> (spec section 4.5:
 *       {@code GroovyNode.equals} is value equality, so two distinct nodes with
 *       the same text are equal — Lua table identity is not);</li>
 *   <li>{@code if}/{@code else}, with Groovy's last-expression-is-the-value rule
 *       lowered to explicit {@code return}s.</li>
 * </ul>
 *
 * <h2>Regex translation (spec section 9.4)</h2>
 * {@code replaceAll}/{@code split}/{@code matches} take {@code java.util.regex}
 * patterns; the target VM (gopher-lua) has only Lua patterns.
 * {@link #groovyRegexToLuaPattern} rewrites the expressible subset and hard-fails
 * on the four measured-unreachable classes — alternation, counted repetition,
 * inline flags/non-capturing groups, and lazy/possessive quantifiers — which
 * together account for the 12.36% of corpus regex uses Lua patterns cannot
 * express. Rewrite rules:
 * <pre>
 *   \d \D \s \S \w \W   -&gt; %d %D %s %S %w %W   (class shorthands)
 *   \&lt;punct&gt;            -&gt; %&lt;punct&gt;            (escaped literal)
 *   %                   -&gt; %%                   (Lua's own escape character)
 *   - (outside a class) -&gt; %-                   (Lua's lazy-repeat quantifier)
 *   ^ $ . * + ? [ ] ( ) -&gt; unchanged            (same meaning in Lua)
 * </pre>
 * Replacement strings are rewritten too: {@code $1} becomes {@code %1} and a
 * literal {@code %} becomes {@code %%}.
 *
 * <p>Test-scope on purpose: this is spike apparatus for the feasibility report,
 * not production code on the Groovy path.
 */
public class GroovySnippetToLua {

    /**
     * The T1 method whitelist. Every name here exists in
     * {@code lua-poc/engine/stdlib.lua} with the same receiver-first argument
     * order, so lowering is a mechanical {@code a.f(b)} -&gt;
     * {@code stdlib.f(a, b)}.
     */
    public static final Set<String> T1_METHODS = new HashSet<>(Arrays.asList(
            "replaceAll", "replace", "capitalize", "split", "trim", "toLowerCase",
            "toUpperCase", "toString", "toInteger", "indexOf", "contains",
            "startsWith", "endsWith", "sanitize", "sanitizeURI", "sanitizeURN",
            "isEmpty", "size", "join", "substring", "matches"));

    /** Methods whose first argument is a java.util.regex pattern. */
    private static final Set<String> REGEX_FIRST_ARG = new HashSet<>(Arrays.asList(
            "replaceAll", "split", "matches"));

    private GroovySnippetToLua() {
    }

    /**
     * Converts a snippet that is a single expression into a Lua expression.
     *
     * @throws UnsupportedConstructException if the snippet is not a single
     *         expression, or uses anything outside T1
     */
    public static String convert(String snippet) {
        BlockStatement block = parse(snippet);
        List<Statement> statements = block.getStatements();
        if (statements.size() != 1 || !(statements.get(0) instanceof ExpressionStatement)) {
            throw new UnsupportedConstructException("MultiStatementSnippet",
                    "convert() takes a single expression; use convertBlock()");
        }
        return expression(((ExpressionStatement) statements.get(0)).getExpression());
    }

    /**
     * Converts a snippet into a Lua statement block whose value is produced by
     * explicit {@code return}s — the form a generated {@code b:elem} content
     * function needs. Groovy's "last expression is the value" rule is made
     * explicit: the trailing expression of every branch becomes a {@code return}.
     */
    public static String convertBlock(String snippet) {
        return statements(parse(snippet).getStatements(), true);
    }

    // ------------------------------------------------------------- parsing

    private static BlockStatement parse(String snippet) {
        List<ASTNode> nodes;
        try {
            nodes = new AstBuilder().buildFromString(CompilePhase.CONVERSION, true, snippet);
        }
        catch (UnsupportedConstructException e) {
            throw e;
        }
        catch (RuntimeException e) {
            throw new UnsupportedConstructException("UnparseableSnippet", e.getMessage());
        }
        for (ASTNode node : nodes) {
            if (node instanceof BlockStatement) return (BlockStatement) node;
        }
        throw new UnsupportedConstructException("UnparseableSnippet", "no block statement produced");
    }

    // ---------------------------------------------------------- statements

    private static String statements(List<Statement> list, boolean valueIsWanted) {
        List<String> out = new ArrayList<>();
        for (int walk = 0; walk < list.size(); walk++) {
            boolean last = walk == list.size() - 1;
            out.add(statement(list.get(walk), valueIsWanted && last));
        }
        return String.join("\n", out);
    }

    private static String statement(Statement statement, boolean valueIsWanted) {
        if (statement instanceof BlockStatement) {
            return statements(((BlockStatement) statement).getStatements(), valueIsWanted);
        }
        if (statement instanceof ReturnStatement) {
            return "return " + expression(((ReturnStatement) statement).getExpression());
        }
        if (statement instanceof ExpressionStatement) {
            String lua = expression(((ExpressionStatement) statement).getExpression());
            if (valueIsWanted) return "return " + lua;
            // A non-final bare expression has no effect in Lua and cannot be a
            // statement there either; in T1 snippets this never legitimately
            // happens, so refuse rather than drop it silently.
            throw new UnsupportedConstructException("DiscardedExpressionStatement", lua);
        }
        if (statement instanceof IfStatement) {
            IfStatement ifStatement = (IfStatement) statement;
            StringBuilder out = new StringBuilder();
            out.append("if ").append(condition(ifStatement.getBooleanExpression().getExpression())).append(" then\n");
            out.append(statement(ifStatement.getIfBlock(), valueIsWanted));
            Statement elseBlock = ifStatement.getElseBlock();
            if (elseBlock != null && !elseBlock.isEmpty()) {
                out.append("\nelse\n").append(statement(elseBlock, valueIsWanted));
            }
            out.append("\nend");
            return out.toString();
        }
        throw new UnsupportedConstructException(statement.getClass().getSimpleName());
    }

    /**
     * A Groovy condition is Groovy-truthy; Lua's only falsy values are
     * {@code false} and {@code nil}, so an empty string would wrongly be true.
     * Comparisons lower directly; anything else is wrapped in the
     * {@code _truthy} helper the generator emits.
     */
    private static String condition(Expression expression) {
        if (expression instanceof BinaryExpression) {
            BinaryExpression binary = (BinaryExpression) expression;
            String op = binary.getOperation().getText();
            if ("==".equals(op) || "!=".equals(op)) {
                return "stdlib.to_text(" + expression(binary.getLeftExpression()) + ") "
                        + ("==".equals(op) ? "==" : "~=")
                        + " stdlib.to_text(" + expression(binary.getRightExpression()) + ")";
            }
        }
        if (expression instanceof NotExpression) {
            return "not (" + condition(((NotExpression) expression).getExpression()) + ")";
        }
        return "_truthy(" + expression(expression) + ")";
    }

    // --------------------------------------------------------- expressions

    private static String expression(Expression expression) {
        if (expression instanceof ConstantExpression) {
            Object value = ((ConstantExpression) expression).getValue();
            if (value == null) return "nil";
            if (value instanceof Number) return value.toString();
            // Booleans included: a bare `true` constant mapping renders as the
            // element text "true" in the reference engine (spec section 1).
            return luaString(String.valueOf(value));
        }
        if (expression instanceof VariableExpression) {
            String name = ((VariableExpression) expression).getName();
            if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new UnsupportedConstructException("VariableExpression", name);
            }
            return name;
        }
        if (expression instanceof GStringExpression) {
            return gstring((GStringExpression) expression);
        }
        if (expression instanceof MethodCallExpression) {
            return methodCall((MethodCallExpression) expression);
        }
        if (expression instanceof org.codehaus.groovy.ast.expr.DeclarationExpression) {
            // A DeclarationExpression IS-A BinaryExpression (`=`), so it has to
            // be recognised before the generic binary case or `def x = 1` gets
            // reported as an assignment rather than as the T3 construct it is.
            throw new UnsupportedConstructException("DeclarationExpression");
        }
        if (expression instanceof BinaryExpression) {
            return binary((BinaryExpression) expression);
        }
        if (expression instanceof NotExpression) {
            return "not (" + condition(((NotExpression) expression).getExpression()) + ")";
        }
        if (expression instanceof TernaryExpression) {
            // Lua's `and`/`or` idiom. Sound here because every T1 branch value is
            // a string or a gstring table, never `false`/`nil` — a Groovy
            // ternary whose true-branch is falsy would translate wrongly, so if
            // that ever appears the golden diff, not the converter, is what
            // catches it. Elvis (`a ?: b`) is an ElvisOperatorExpression, a
            // TernaryExpression subclass, and lowers the same way.
            TernaryExpression ternary = (TernaryExpression) expression;
            return "((" + condition(ternary.getBooleanExpression().getExpression()) + ") and ("
                    + expression(ternary.getTrueExpression()) + ") or ("
                    + expression(ternary.getFalseExpression()) + "))";
        }
        throw new UnsupportedConstructException(expression.getClass().getSimpleName());
    }

    private static String gstring(GStringExpression gstring) {
        StringBuilder parts = new StringBuilder("{");
        List<ConstantExpression> strings = gstring.getStrings();
        for (int walk = 0; walk < strings.size(); walk++) {
            if (walk > 0) parts.append(", ");
            parts.append(luaString(String.valueOf(strings.get(walk).getValue())));
        }
        parts.append("}");

        StringBuilder slots = new StringBuilder("{");
        List<Expression> values = gstring.getValues();
        for (int walk = 0; walk < values.size(); walk++) {
            if (walk > 0) slots.append(", ");
            // stdlib.gs's contract: slot_texts must be already-stringified and
            // hole-free, so a nil slot becomes the literal word "null" — Groovy
            // interpolates a null value as "null" (spec section 4.6).
            slots.append("stdlib.to_text(").append(expression(values.get(walk))).append(") or \"null\"");
        }
        slots.append("}");

        return "stdlib.gs(" + parts + ", " + slots + ")";
    }

    private static String methodCall(MethodCallExpression call) {
        if (!(call.getMethod() instanceof ConstantExpression)) {
            throw new UnsupportedConstructException("DynamicMethodName");
        }
        String name = String.valueOf(((ConstantExpression) call.getMethod()).getValue());
        if (call.isImplicitThis()) {
            // A bare `foo(...)` call: a mapping-defined or rec-def-defined
            // Groovy function. Real Groovy, not stdlib — outside T1 by
            // definition, and named so the report can count them.
            throw new UnsupportedConstructException("MethodCall:" + name);
        }
        if (!T1_METHODS.contains(name)) {
            throw new UnsupportedConstructException("MethodCall:" + name);
        }
        List<Expression> args = argumentList(call);
        StringBuilder out = new StringBuilder("stdlib.").append(name).append("(");
        out.append(expression(call.getObjectExpression()));
        for (int walk = 0; walk < args.size(); walk++) {
            out.append(", ");
            Expression arg = args.get(walk);
            if (walk == 0 && REGEX_FIRST_ARG.contains(name)) {
                out.append(luaString(groovyRegexToLuaPattern(constantString(arg, name + " pattern"))));
            }
            else if (walk == 1 && "replaceAll".equals(name)) {
                out.append(luaString(groovyReplacementToLua(constantString(arg, "replaceAll replacement"))));
            }
            else {
                out.append(expression(arg));
            }
        }
        return out.append(")").toString();
    }

    private static List<Expression> argumentList(MethodCallExpression call) {
        Expression arguments = call.getArguments();
        if (arguments instanceof org.codehaus.groovy.ast.expr.ArgumentListExpression) {
            return ((org.codehaus.groovy.ast.expr.ArgumentListExpression) arguments).getExpressions();
        }
        if (arguments instanceof org.codehaus.groovy.ast.expr.TupleExpression) {
            return ((org.codehaus.groovy.ast.expr.TupleExpression) arguments).getExpressions();
        }
        throw new UnsupportedConstructException(arguments.getClass().getSimpleName());
    }

    private static String constantString(Expression expression, String what) {
        if (expression instanceof ConstantExpression) {
            Object value = ((ConstantExpression) expression).getValue();
            if (value instanceof String) return (String) value;
        }
        // A pattern held in a variable cannot be translated at codegen time;
        // section 9.4's measurement excluded these too.
        throw new UnsupportedConstructException("NonLiteralRegexArgument", what);
    }

    private static String binary(BinaryExpression binary) {
        String op = binary.getOperation().getText();
        if ("[".equals(op)) {
            String key = constantString(binary.getRightExpression(), "subscript key");
            // MappingCategory.TupleMap.get returns "" for a missing key rather
            // than null; _tget reproduces that (spec section 2.4).
            return "_tget(" + expression(binary.getLeftExpression()) + ", " + luaString(key) + ")";
        }
        if ("==".equals(op) || "!=".equals(op)) {
            return condition(binary);
        }
        // The Groovy list operators (`*`, `**`, `>>`, `|`) are BinaryExpressions
        // whose right operand is a closure. Reporting "BinaryExpression:*" would
        // name the syntax; the closure is what actually puts the snippet in T2,
        // so report that instead — the report counts constructs, not tokens.
        if (binary.getRightExpression() instanceof org.codehaus.groovy.ast.expr.ClosureExpression
                || binary.getLeftExpression() instanceof org.codehaus.groovy.ast.expr.ClosureExpression) {
            throw new UnsupportedConstructException("ClosureExpression", "operand of `" + op + "`");
        }
        throw new UnsupportedConstructException("BinaryExpression:" + op);
    }

    // ------------------------------------------------- regex -> Lua pattern

    /**
     * Rewrites a {@code java.util.regex} pattern as a Lua pattern, or refuses.
     * See the class javadoc for the rule table and
     * {@code docs/specs/mapping-language-core.md} section 9.4 for the corpus
     * measurement behind the refusal list.
     */
    public static String groovyRegexToLuaPattern(String regex) {
        StringBuilder out = new StringBuilder();
        boolean inClass = false;
        for (int walk = 0; walk < regex.length(); walk++) {
            char c = regex.charAt(walk);
            switch (c) {
                case '\\': {
                    if (walk + 1 >= regex.length()) {
                        throw new UnsupportedConstructException("RegexDanglingEscape", regex);
                    }
                    char next = regex.charAt(++walk);
                    if ("dDsSwW".indexOf(next) >= 0) {
                        out.append('%').append(next);
                    }
                    else if (Character.isLetterOrDigit(next)) {
                        // \b \A \Z \1 ... : word boundaries, anchors and
                        // backreferences have no Lua equivalent.
                        throw new UnsupportedConstructException("RegexUnsupportedEscape", "\\" + next);
                    }
                    else {
                        out.append('%').append(next);
                    }
                    break;
                }
                case '[':
                    inClass = true;
                    out.append('[');
                    break;
                case ']':
                    inClass = false;
                    out.append(']');
                    break;
                case '|':
                    if (!inClass) throw new UnsupportedConstructException("RegexAlternation", regex);
                    out.append("%|");
                    break;
                case '{':
                    if (!inClass) throw new UnsupportedConstructException("RegexCountedRepetition", regex);
                    out.append("%{");
                    break;
                case '(':
                    if (!inClass && walk + 1 < regex.length() && regex.charAt(walk + 1) == '?') {
                        throw new UnsupportedConstructException("RegexInlineFlagOrGroup", regex);
                    }
                    out.append('(');
                    break;
                case '*':
                case '+':
                case '?':
                    out.append(c);
                    if (!inClass && walk + 1 < regex.length()) {
                        char next = regex.charAt(walk + 1);
                        if (next == '?') throw new UnsupportedConstructException("RegexLazyQuantifier", regex);
                        if (next == '+') throw new UnsupportedConstructException("RegexPossessiveQuantifier", regex);
                    }
                    break;
                case '%':
                    out.append("%%");
                    break;
                case '-':
                    // Literal in java.util.regex outside a class; Lua's lazy
                    // repeat operator, so it must be escaped there.
                    out.append(inClass ? "-" : "%-");
                    break;
                default:
                    out.append(c);
            }
        }
        return out.toString();
    }

    /** Rewrites a java.util.regex replacement string for Lua's {@code gsub}. */
    public static String groovyReplacementToLua(String replacement) {
        StringBuilder out = new StringBuilder();
        for (int walk = 0; walk < replacement.length(); walk++) {
            char c = replacement.charAt(walk);
            if (c == '%') {
                out.append("%%");
            }
            else if (c == '$' && walk + 1 < replacement.length()
                    && Character.isDigit(replacement.charAt(walk + 1))) {
                out.append('%').append(replacement.charAt(++walk));
            }
            else if (c == '\\' && walk + 1 < replacement.length()) {
                out.append(replacement.charAt(++walk));
            }
            else {
                out.append(c);
            }
        }
        return out.toString();
    }

    // -------------------------------------------------------------- output

    /** Renders a Java string as a double-quoted Lua string literal. */
    public static String luaString(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int walk = 0; walk < value.length(); walk++) {
            char c = value.charAt(walk);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    out.append(c);
            }
        }
        return out.append("\"").toString();
    }
}
