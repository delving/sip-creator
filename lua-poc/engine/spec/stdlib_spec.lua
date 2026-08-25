-- Task 7: pure-Lua T1 stdlib (MappingCategory + GroovyNode + plain-String
-- method port).
--
-- Contract under test (task-7-brief.md + docs/specs/mapping-language-core.md
-- section 4 "Stdlib" + section 5 "Null propagation"):
--
--   Every function accepts a string, a Task-6 node, or a Task-6 absorber as
--   its first ("receiver") argument, coerced via stdlib's internal to_text
--   (node/absorber -> :text(), string -> itself, a plain Lua array -> a
--   bracket-joined "[a, b]" rendering per spec section 1/4.3).
--
--   DELIBERATE DEVIATION from spec section 5.2's per-function null table,
--   spelled out in stdlib.lua's header and task-7-report.md: the brief's
--   own worked example is `assert.equal("", stdlib.capitalize(nil))` —
--   i.e. a nil/absorber *receiver* is null-safe and coerces to "", it does
--   NOT reproduce Groovy's NPE-at-call-site. This keeps the engine total
--   (matching Task 6's absorber design) instead of crash-prone on every
--   absent-field access. A nil *later* argument (a pattern, delimiter,
--   needle — a code-level literal, not absent data) is still treated as a
--   programmer error and raises a Lua error, which IS what the null table
--   specifies for those positions.

local stdlib = require("stdlib")
local node = require("node")
local ABSORBER = node.absorber

describe("replaceAll(s, pattern, repl) — spec 4.1 #1", function()
  it("replaces every match (Lua pattern, not java.util.regex — converter's job, spec 9.4)", function()
    assert.equal("x_y", stdlib.replaceAll("x y", "%s", "_"))
  end)

  it("nil receiver coerces to '' (no match, no error)", function()
    assert.equal("", stdlib.replaceAll(nil, "x", "y"))
  end)

  it("absorber receiver coerces to ''", function()
    assert.equal("", stdlib.replaceAll(ABSORBER, "x", "y"))
  end)

  it("nil pattern is a programmer error (Lua error, not silently absorbed)", function()
    assert.has_error(function() stdlib.replaceAll("x", nil, "y") end)
  end)

  it("nil replacement is a programmer error", function()
    assert.has_error(function() stdlib.replaceAll("x", "x", nil) end)
  end)
end)

describe("split(s, pattern[, limit]) — spec 4.1 #2/#3", function()
  it("splits on a pattern", function()
    assert.same({ "a", "b", "c" }, stdlib.split("a,b,c", ","))
  end)

  it("strips trailing empty strings (JDK default split semantics)", function()
    assert.same({ "a", "b" }, stdlib.split("a,b,,", ","))
  end)

  it("',' split on itself yields an empty array (all-trailing-empty case)", function()
    assert.same({}, stdlib.split(",", ","))
  end)

  it("empty input yields a single empty-string element, not an empty array", function()
    assert.same({ "" }, stdlib.split("", ","))
  end)

  it("nil receiver behaves like empty input", function()
    assert.same({ "" }, stdlib.split(nil, ","))
  end)

  it("absorber receiver behaves like empty input", function()
    assert.same({ "" }, stdlib.split(ABSORBER, ","))
  end)

  it("respects an explicit positive limit", function()
    assert.same({ "a", "b,c" }, stdlib.split("a,b,c", ",", 2))
  end)

  it("nil pattern is a programmer error", function()
    assert.has_error(function() stdlib.split("a,b", nil) end)
  end)
end)

describe("matches(s, pattern) — spec 4.1 #4", function()
  it("whole-string match", function()
    assert.is_true(stdlib.matches("abc123", "%a+%d+"))
  end)

  it("rejects a partial match", function()
    assert.is_false(stdlib.matches("xabc123y", "%a+%d+"))
  end)

  it("nil receiver coerces to '' (no match)", function()
    assert.is_false(stdlib.matches(nil, "%a+"))
  end)

  it("absorber receiver coerces to ''", function()
    assert.is_false(stdlib.matches(ABSORBER, "%a+"))
  end)

  it("nil pattern is a programmer error", function()
    assert.has_error(function() stdlib.matches("x", nil) end)
  end)
end)

describe("asBoolean(list) — spec 4.1 #7", function()
  it("empty list is false", function()
    assert.is_false(stdlib.asBoolean({}))
  end)

  it("a non-empty list is true", function()
    assert.is_true(stdlib.asBoolean({ "a" }))
  end)

  it("[[]] (a single nested empty list) is false", function()
    assert.is_false(stdlib.asBoolean({ {} }))
  end)

  it("nil is falsy, not dispatched (no error)", function()
    assert.is_false(stdlib.asBoolean(nil))
  end)
end)

describe("sanitize(v) — spec 4.1 #19/#20/#21 (node/list/text overloads unified via coercion)", function()
  it("collapses newlines to spaces then runs of spaces to one", function()
    assert.equal("a b c", stdlib.sanitize("a\nb   c"))
  end)

  it("does not trim leading/trailing space", function()
    assert.equal(" a ", stdlib.sanitize(" a "))
  end)

  it("nil receiver coerces to ''", function()
    assert.equal("", stdlib.sanitize(nil))
  end)

  it("absorber receiver coerces to ''", function()
    assert.equal("", stdlib.sanitize(ABSORBER))
  end)
end)

describe("sanitizeURI(v) — spec 4.1 #22", function()
  it("percent-encodes only space, [, ], and backslash", function()
    assert.equal("a%20b%5Bc%5D%5Cd", stdlib.sanitizeURI("a b[c]\\d"))
  end)

  it("leaves other punctuation unencoded", function()
    assert.equal("a:b/c", stdlib.sanitizeURI("a:b/c"))
  end)

  it("nil receiver coerces to '' (DEVIATION: spec 5.2 says NPE at o.toString() — see header)", function()
    assert.equal("", stdlib.sanitizeURI(nil))
  end)

  it("absorber receiver coerces to ''", function()
    assert.equal("", stdlib.sanitizeURI(ABSORBER))
  end)
end)

describe("sanitizeURN(v) — spec 4.1 #23, the only null-safe category function in Groovy too", function()
  it("replaces : / _ space [ ] backslash with '-' and collapses runs", function()
    assert.equal("a-b-c-d-e-f-g", stdlib.sanitizeURN("a:b/c_d[e]f\\g"))
  end)

  it("collapses adjacent separators into a single '-'", function()
    assert.equal("a-b", stdlib.sanitizeURN("a: /b"))
  end)

  it("nil -> '' (explicit null guard, matches spec exactly)", function()
    assert.equal("", stdlib.sanitizeURN(nil))
  end)

  it("absorber -> ''", function()
    assert.equal("", stdlib.sanitizeURN(ABSORBER))
  end)
end)

describe("indexOf(v, s) — spec 4.1 #9 / 4.3, literal substring search", function()
  it("returns the 0-based index of a literal match", function()
    assert.equal(2, stdlib.indexOf("abcabc", "c"))
  end)

  it("returns -1 when absent", function()
    assert.equal(-1, stdlib.indexOf("abc", "z"))
  end)

  it("treats the needle as a literal, not a pattern", function()
    assert.equal(1, stdlib.indexOf("a.b", "."))
  end)

  it("nil receiver coerces to '' -> -1", function()
    assert.equal(-1, stdlib.indexOf(nil, "x"))
  end)

  it("absorber receiver coerces to '' -> -1", function()
    assert.equal(-1, stdlib.indexOf(ABSORBER, "x"))
  end)

  it("nil needle is a programmer error", function()
    assert.has_error(function() stdlib.indexOf("abc", nil) end)
  end)
end)

describe("substring(v, from[, to]) — spec 4.1 #10/#11, Java 0-based/exclusive-end semantics", function()
  it("substring(from) drops the first `from` characters", function()
    assert.equal("cde", stdlib.substring("abcde", 2))
  end)

  it("substring(from, to) is a [from, to) slice", function()
    assert.equal("bc", stdlib.substring("abcde", 1, 3))
  end)

  it("out-of-range from throws", function()
    assert.has_error(function() stdlib.substring("abc", 10) end)
  end)

  it("to > length throws", function()
    assert.has_error(function() stdlib.substring("abc", 0, 10) end)
  end)

  it("nil receiver coerces to '' (substring(0) of '' is '')", function()
    assert.equal("", stdlib.substring(nil, 0))
  end)
end)

describe("replace(v, a, b) — spec 4.3, LITERAL replacement (distinct from replaceAll)", function()
  it("replaces a literal substring, not a pattern", function()
    assert.equal("a_b_c", stdlib.replace("a.b.c", ".", "_"))
  end)

  it("a pattern-magic character in `a` is treated literally", function()
    assert.equal("aXbXc", stdlib.replace("a.b.c", ".", "X"))
  end)

  it("nil receiver coerces to ''", function()
    assert.equal("", stdlib.replace(nil, "x", "y"))
  end)

  it("nil `a` is a programmer error", function()
    assert.has_error(function() stdlib.replace("x", nil, "y") end)
  end)
end)

describe("capitalize(v) — spec 4.3, brief's own worked example", function()
  it("uppercases only the first character", function()
    assert.equal("Abc", stdlib.capitalize("abc"))
  end)

  it("empty string stays empty", function()
    assert.equal("", stdlib.capitalize(""))
  end)

  it("nil receiver -> '' (brief's literal example)", function()
    assert.equal("", stdlib.capitalize(nil))
  end)

  it("absorber receiver -> ''", function()
    assert.equal("", stdlib.capitalize(ABSORBER))
  end)
end)

describe("trim(v) — spec 4.3", function()
  it("strips both ends", function()
    assert.equal("a b", stdlib.trim("  a b  "))
  end)

  it("nil receiver -> ''", function()
    assert.equal("", stdlib.trim(nil))
  end)
end)

describe("toLowerCase(v) / toUpperCase(v) — spec 4.3", function()
  it("lowercases", function()
    assert.equal("abc", stdlib.toLowerCase("ABC"))
  end)

  it("uppercases", function()
    assert.equal("ABC", stdlib.toUpperCase("abc"))
  end)

  it("nil receiver -> ''", function()
    assert.equal("", stdlib.toLowerCase(nil))
    assert.equal("", stdlib.toUpperCase(nil))
  end)
end)

describe("toString(v) — spec 4.3/2.6/section 1", function()
  it("is identity for a string", function()
    assert.equal("abc", stdlib.toString("abc"))
  end)

  it("renders a node's text", function()
    local r = node.parse("<r>hello</r>")
    assert.equal("hello", stdlib.toString(r))
  end)

  it("renders a plain list as '[a, b]' (NodeList.toString, spec section 1)", function()
    assert.equal("[a, b]", stdlib.toString({ "a", "b" }))
  end)

  it("absorber renders as ''", function()
    assert.equal("", stdlib.toString(ABSORBER))
  end)

  it("nil receiver -> '' (DEVIATION: spec says NPE — see header/report)", function()
    assert.equal("", stdlib.toString(nil))
  end)
end)

describe("toInteger(v) — spec 4.3, Groovy GDK Integer.valueOf after trim", function()
  it("parses a plain integer", function()
    assert.equal(42, stdlib.toInteger(" 42 "))
  end)

  it("parses a negative integer", function()
    assert.equal(-7, stdlib.toInteger("-7"))
  end)

  it("throws on junk (NumberFormatException-equivalent)", function()
    assert.has_error(function() stdlib.toInteger("abc") end)
  end)

  it("nil receiver coerces to '' -> throws, same as Groovy's \"\".toInteger()", function()
    assert.has_error(function() stdlib.toInteger(nil) end)
  end)
end)

describe("contains / startsWith / endsWith(v, s) — spec 4.2/4.3", function()
  it("contains finds a substring", function()
    assert.is_true(stdlib.contains("abcdef", "cd"))
    assert.is_false(stdlib.contains("abcdef", "zz"))
  end)

  it("startsWith / endsWith", function()
    assert.is_true(stdlib.startsWith("abc", "ab"))
    assert.is_true(stdlib.endsWith("abc", "bc"))
    assert.is_false(stdlib.startsWith("abc", "bc"))
  end)

  it("nil receivers coerce to ''", function()
    assert.is_false(stdlib.contains(nil, "x"))
    assert.is_false(stdlib.startsWith(nil, "x"))
    assert.is_false(stdlib.endsWith(nil, "x"))
  end)

  it("nil needle is a programmer error", function()
    assert.has_error(function() stdlib.contains("abc", nil) end)
  end)
end)

describe("isEmpty(v) — spec 4.3, length check, no trim", function()
  it("true for empty string", function()
    assert.is_true(stdlib.isEmpty(""))
  end)

  it("false for whitespace-only (isEmpty does not trim)", function()
    assert.is_false(stdlib.isEmpty(" "))
  end)

  it("nil receiver -> true (coerces to '')", function()
    assert.is_true(stdlib.isEmpty(nil))
  end)
end)

describe("size(v) — spec 4.2/4.3: string/node = text length, list = element count", function()
  it("string length", function()
    assert.equal(3, stdlib.size("abc"))
  end)

  it("node text length, NOT child count (the documented trap, spec 4.2)", function()
    local r = node.parse("<r>abcde<x/><y/></r>")
    assert.equal(5, stdlib.size(r))
  end)

  it("list element count", function()
    assert.equal(2, stdlib.size({ "a", "b" }))
  end)

  it("nil receiver -> 0", function()
    assert.equal(0, stdlib.size(nil))
  end)
end)

describe("join(list, sep) — spec 4.3", function()
  it("stringifies and joins elements", function()
    assert.equal("a-b-c", stdlib.join({ "a", "b", "c" }, "-"))
  end)

  it("nil list is a programmer error (no list to join)", function()
    assert.has_error(function() stdlib.join(nil, ",") end)
  end)
end)

describe("gs(parts, slot_texts) — spec 3.4 suppressIfAllVariablesEmpty (Builder-output hazard a)", function()
  it("renders literal parts interleaved with slot texts", function()
    local g = stdlib.gs({ "http://x/", "/graph" }, { "abc" })
    assert.equal("http://x/abc/graph", g.text)
    assert.is_false(g.suppressed)
  end)

  it("suppresses when every slot trims to empty", function()
    local g = stdlib.gs({ "http://x/", "/graph" }, { "" })
    assert.is_true(g.suppressed)
  end)

  it("suppresses when every one of multiple slots trims to empty", function()
    local g = stdlib.gs({ "a", "b", "c" }, { "", "  " })
    assert.is_true(g.suppressed)
  end)

  it("does not suppress if at least one slot is non-empty", function()
    local g = stdlib.gs({ "a", "-", "c" }, { "", "x" })
    assert.is_false(g.suppressed)
  end)

  it("a template with zero slots is never suppressed (plain-string rule, spec 3.4)", function()
    local g = stdlib.gs({ "literal" }, {})
    assert.is_false(g.suppressed)
    assert.equal("literal", g.text)
  end)

  it("a nil-rendered slot ('null' the 4-char word) is non-empty and does not suppress alone", function()
    local g = stdlib.gs({ "a=", "" }, { "null" })
    assert.is_false(g.suppressed)
    assert.equal("a=null", g.text)
  end)
end)
