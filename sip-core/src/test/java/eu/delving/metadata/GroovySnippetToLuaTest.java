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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Task 8 of the Lua mapping-engine feasibility spike.
 *
 * <p>Pins the Groovy-snippet -> Lua translation for the T1 tier and the
 * hard-fail behaviour for everything outside it. See
 * {@code docs/specs/mapping-language-core.md} for the semantic contract and
 * {@code lua-poc/engine/} for the runtime the emitted code targets.
 */
public class GroovySnippetToLuaTest {

    // ---------------------------------------------------------------- T1

    @Test
    public void methodCallLowersToStdlibWithReceiverFirst() {
        assertEquals(
                "stdlib.replaceAll(input_, \"^0+\", \"\")",
                GroovySnippetToLua.convert("input_.replaceAll('^0+','')"));
    }

    @Test
    public void gstringLowersToStdlibGs() {
        assertEquals(
                "stdlib.gs({\"\", \"\"}, {stdlib.to_text(stdlib.sanitizeURI(input_)) or \"null\"})",
                GroovySnippetToLua.convert("\"${input_.sanitizeURI()}\""));
    }

    @Test
    public void gstringKeepsLiteralPartsAroundSlots() {
        assertEquals(
                "stdlib.gs({\"\", \"/resource/\", \"\"}, "
                        + "{stdlib.to_text(baseUrl) or \"null\", stdlib.to_text(spec) or \"null\"})",
                GroovySnippetToLua.convert("\"${baseUrl}/resource/${spec}\""));
    }

    @Test
    public void stringConstantLowersToLuaString() {
        assertEquals("\"Noord-Holland\"", GroovySnippetToLua.convert("'Noord-Holland'"));
    }

    @Test
    public void booleanConstantLowersToLuaString() {
        // The reference engine renders a bare `true`/`false` constant mapping as
        // element text, i.e. the string "true"/"false" (spec section 1).
        assertEquals("\"true\"", GroovySnippetToLua.convert("true"));
    }

    @Test
    public void tupleMapSubscriptLowersToTgetHelper() {
        assertEquals(
                "_tget(_M4, \"voornaam1\")",
                GroovySnippetToLua.convert("_M4['voornaam1']"));
    }

    @Test
    public void nestedMethodCallsChain() {
        assertEquals(
                "stdlib.replaceAll(stdlib.capitalize(x), \"^Ij\", \"IJ\")",
                GroovySnippetToLua.convert("x.capitalize().replaceAll('^Ij','IJ')"));
    }

    // ------------------------------------------------ statement blocks

    @Test
    public void ifElseLowersToLuaIfElseWithReturns() {
        String lua = GroovySnippetToLua.convertBlock(
                "if (_M4['a'] != _M4['b']) {\n"
                        + "\"${_M4['a']}\"\n"
                        + "}\n"
                        + "else {\n"
                        + "\"${_M4['b']}\"\n"
                        + "}");
        assertEquals(
                "if stdlib.to_text(_tget(_M4, \"a\")) ~= stdlib.to_text(_tget(_M4, \"b\")) then\n"
                        + "return stdlib.gs({\"\", \"\"}, {stdlib.to_text(_tget(_M4, \"a\")) or \"null\"})\n"
                        + "else\n"
                        + "return stdlib.gs({\"\", \"\"}, {stdlib.to_text(_tget(_M4, \"b\")) or \"null\"})\n"
                        + "end",
                lua);
    }

    @Test
    public void singleExpressionBlockGetsReturn() {
        assertEquals("return \"x\"", GroovySnippetToLua.convertBlock("'x'"));
    }

    // ------------------------------------------- Groovy regex -> Lua pattern
    //
    // Translation rules (spec section 9.4). A java.util.regex pattern is
    // rewritten to a Lua pattern character by character:
    //
    //   \d \D \s \S \w \W   ->  %d %D %s %S %w %W        (class shorthands)
    //   \. \[ \\ \$ ...     ->  %. %[ %% %$              (escaped literals)
    //   ^ $ . * + ? [ ] ( ) ->  kept (same meaning in Lua)
    //   %                   ->  %%                       (Lua's own escape char)
    //   - (literal, outside a class) -> %-                (Lua's lazy quantifier)
    //
    // and hard-fails on the four constructs Lua patterns cannot express, which
    // the corpus scan measured at 12.36% of all regex uses:
    //   alternation `|`, counted repetition `{n,m}`,
    //   inline flags / non-capturing groups `(?...)`, lazy quantifiers `*?`.

    @Test
    public void regexClassShorthandsBecomeLuaPercentClasses() {
        assertEquals("stdlib.matches(x, \"^%d+$\")", GroovySnippetToLua.convert("x.matches('^\\\\d+$')"));
    }

    @Test
    public void regexEscapedLiteralBecomesLuaPercentEscape() {
        assertEquals("stdlib.replaceAll(x, \"%.jpg\", \"\")", GroovySnippetToLua.convert("x.replaceAll('\\\\.jpg','')"));
    }

    @Test
    public void regexLiteralDashIsEscapedForLua() {
        assertEquals("stdlib.split(x, \"%-\")", GroovySnippetToLua.convert("x.split('-')"));
    }

    @Test
    public void regexAlternationHardFails() {
        UnsupportedConstructException e = assertThrows(UnsupportedConstructException.class,
                () -> GroovySnippetToLua.convert("x.replaceAll('cat|dog','')"));
        assertEquals("RegexAlternation", e.getConstructName());
    }

    @Test
    public void regexCountedRepetitionHardFails() {
        UnsupportedConstructException e = assertThrows(UnsupportedConstructException.class,
                () -> GroovySnippetToLua.convert("x.replaceAll('[ ]{2,15}',' ')"));
        assertEquals("RegexCountedRepetition", e.getConstructName());
    }

    @Test
    public void regexInlineFlagsHardFail() {
        UnsupportedConstructException e = assertThrows(UnsupportedConstructException.class,
                () -> GroovySnippetToLua.convert("x.replaceAll('(?i)abc','')"));
        assertEquals("RegexInlineFlagOrGroup", e.getConstructName());
    }

    @Test
    public void regexLazyQuantifierHardFails() {
        UnsupportedConstructException e = assertThrows(UnsupportedConstructException.class,
                () -> GroovySnippetToLua.convert("x.replaceAll('thumb/.*?/','')"));
        assertEquals("RegexLazyQuantifier", e.getConstructName());
    }

    // ------------------------------------------------------- hard failures

    @Test
    public void declarationHardFailsWithConstructName() {
        UnsupportedConstructException e = assertThrows(UnsupportedConstructException.class,
                () -> GroovySnippetToLua.convertBlock("def x = 1"));
        assertEquals("DeclarationExpression", e.getConstructName());
        assertTrue(e.getMessage().contains("DeclarationExpression"), e.getMessage());
    }

    @Test
    public void closureHardFailsWithConstructName() {
        UnsupportedConstructException e = assertThrows(UnsupportedConstructException.class,
                () -> GroovySnippetToLua.convertBlock("_input.record * { _record -> \"${_record}\" }"));
        assertEquals("ClosureExpression", e.getConstructName());
    }

    @Test
    public void listLiteralHardFailsWithConstructName() {
        UnsupportedConstructException e = assertThrows(UnsupportedConstructException.class,
                () -> GroovySnippetToLua.convert("['a','b']"));
        assertEquals("ListExpression", e.getConstructName());
    }

    @Test
    public void unknownFunctionCallHardFailsNamingTheFunction() {
        UnsupportedConstructException e = assertThrows(UnsupportedConstructException.class,
                () -> GroovySnippetToLua.convert("reverseNames(\"${x}\")"));
        assertEquals("MethodCall:reverseNames", e.getConstructName());
    }

    @Test
    public void nonT1MethodHardFailsNamingTheMethod() {
        UnsupportedConstructException e = assertThrows(UnsupportedConstructException.class,
                () -> GroovySnippetToLua.convert("x.getValueNodes('thumbnaillarge')"));
        assertEquals("MethodCall:getValueNodes", e.getConstructName());
    }
}
