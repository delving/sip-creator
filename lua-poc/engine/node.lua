--[==[
node.lua — GroovyNode-shaped navigation over the tree built by xml.lua.

Implements the Task 6 surface (task-6-brief.md) over the semantic contract
in docs/specs/mapping-language-core.md sections 2 "Navigation" and 5 "Null
propagation":

  node.parse(xml_string) -> root
  n:get(name)  -> list of direct children whose normalised local name
                  matches (spec 2.3, the "anything else" row: direct
                  children only, no text filter, empty list on miss)
  n:get_(name) -> first descendant (self included, pre-order) whose
                  normalised name matches AND whose text is non-empty
                  (spec 2.3, the "_" row); an absorber on miss
  n:attr(name) -> the qualified attribute's value, or nil on a real
                  node's miss
  n:text()     -> trimmed accumulated text, CDATA re-wrapped as a
                  literal "<![CDATA[...]]>" marker (spec 2.1)
  tostring(n)  == n:text()

Deferred (narrower than the full spec — see task-6-report.md):
  - The "@attr" and "*" lookup forms (spec 2.3 rows 1 and 2) are not
    exposed; `attr()` covers the "@" case's value but returns a bare
    string/nil rather than a list, and there is no `get("*")` sugar.
  - `getValueNodes` (spec 2.5, recursive multi-match collection) is not
    implemented; only get_ (first match) and get (direct children) are.
  - node.equals/hashCode's value-equality-with-strings (spec 2.6) is not
    reproduced; Lua equality on node objects is identity only.
  - The spec's three distinct absent values (empty list / "" / null,
    spec 5.1) collapse here into two observables (a real empty table
    from `get`, and the single shared absorber from `get_` miss/`attr`
    miss-on-absorber). The absorber deliberately behaves like "" for
    :text()/:is_empty() but stays navigable, which is NOT what the
    Groovy runtime does (a real "" string has no .get_ method there) —
    it is this engine's chosen mechanism for making chained navigation
    total. Task 7/8, which build the null-propagation/truth-table logic
    on top of this, must decide whether callers ever need to tell "get()
    miss" and "get_() miss" apart as failure modes; today both are
    falsy-in-the-relevant-sense but are different Lua types (table vs.
    absorber-with-metatable).
]==]

local xmlparser = require("xml")

-- StringUtil.PLAIN_ASCII / UNICODE accent-folding table, transcribed byte-for-byte
-- from sip-core/src/main/java/eu/delving/metadata/StringUtil.java:172-190.
-- Keys are the 2-byte UTF-8 encoding of each Latin-1/Latin-Extended-A codepoint
-- (all fold targets are <= U+0178, i.e. within the 2-byte UTF-8 range), because
-- Lua 5.1 strings are byte strings with no Unicode awareness (spec section 9.3.4).
local ACCENTS = {
  -- grave
  ["\195\128"] = "A",
  ["\195\160"] = "a",
  ["\195\136"] = "E",
  ["\195\168"] = "e",
  ["\195\140"] = "I",
  ["\195\172"] = "i",
  ["\195\146"] = "O",
  ["\195\178"] = "o",
  ["\195\153"] = "U",
  ["\195\185"] = "u",
  -- acute
  ["\195\129"] = "A",
  ["\195\161"] = "a",
  ["\195\137"] = "E",
  ["\195\169"] = "e",
  ["\195\141"] = "I",
  ["\195\173"] = "i",
  ["\195\147"] = "O",
  ["\195\179"] = "o",
  ["\195\154"] = "U",
  ["\195\186"] = "u",
  ["\195\157"] = "Y",
  ["\195\189"] = "y",
  -- circumflex
  ["\195\130"] = "A",
  ["\195\162"] = "a",
  ["\195\138"] = "E",
  ["\195\170"] = "e",
  ["\195\142"] = "I",
  ["\195\174"] = "i",
  ["\195\148"] = "O",
  ["\195\180"] = "o",
  ["\195\155"] = "U",
  ["\195\187"] = "u",
  ["\197\182"] = "Y",
  ["\197\183"] = "y",
  -- tilde
  ["\195\131"] = "A",
  ["\195\163"] = "a",
  ["\195\149"] = "O",
  ["\195\181"] = "o",
  ["\195\145"] = "N",
  ["\195\177"] = "n",
  -- umlaut
  ["\195\132"] = "A",
  ["\195\164"] = "a",
  ["\195\139"] = "E",
  ["\195\171"] = "e",
  ["\195\143"] = "I",
  ["\195\175"] = "i",
  ["\195\150"] = "O",
  ["\195\182"] = "o",
  ["\195\156"] = "U",
  ["\195\188"] = "u",
  ["\197\184"] = "Y",
  ["\195\191"] = "y",
  -- ring
  ["\195\133"] = "A",
  ["\195\165"] = "a",
  -- cedilla
  ["\195\135"] = "C",
  ["\195\167"] = "c",
  -- double acute
  ["\197\144"] = "O",
  ["\197\145"] = "o",
  ["\197\176"] = "U",
  ["\197\177"] = "u",
}

-- StringUtil.DELETED = "-.;:_" (StringUtil.java:180)
local DELETED = { ["-"] = true, ["."] = true, [";"] = true, [":"] = true, ["_"] = true }

-- StringUtil.tagToVariable (StringUtil.java:192-208), byte-string version.
local function tagToVariable(s)
  if s == nil then
    return nil
  end
  local out = {}
  local n = #s
  local i = 1
  while i <= n do
    local pair = s:sub(i, i + 1)
    local folded = #pair == 2 and ACCENTS[pair]
    if folded then
      out[#out + 1] = folded
      i = i + 2
    else
      local c = s:sub(i, i)
      if not DELETED[c] then
        out[#out + 1] = c
      end
      i = i + 1
    end
  end
  return table.concat(out)
end

local function trim(s)
  return (s:gsub("^%s+", ""):gsub("%s+$", ""))
end

-- Utils.stripNonPrinting strips [\p{Cc}&&[^\r\n\t]] (Utils.java:96-100), i.e.
-- C0 controls and DEL other than tab/newline/CR. Only the ASCII C0/DEL range
-- is handled: Lua 5.1 strings are bytes, and the C1 control range Java's
-- \p{Cc} also covers (U+0080-U+009F) is indistinguishable, byte-for-byte,
-- from UTF-8 continuation bytes without full decoding — stripping it here
-- would corrupt any accented/multi-byte character. Left unhandled; the
-- input corpus is well-formed UTF-8 XML and C1 controls do not occur.
local function stripNonPrinting(s)
  return (s:gsub("[\1-\8\11\12\14-\31\127]", ""))
end

local Node = {}
Node.__index = Node

local Absorber = {}
Absorber.__index = Absorber
local ABSORBER = setmetatable({}, Absorber)

function Absorber:get(_name)
  return {}
end

function Absorber:get_(_name)
  return ABSORBER
end

function Absorber:attr(_name)
  return ""
end

function Absorber:text()
  return ""
end

function Absorber:is_empty()
  return true
end

Absorber.__tostring = function()
  return ""
end

local function wrap(raw)
  if raw == nil then
    return ABSORBER
  end
  if not raw._node then
    raw._node = setmetatable({ raw = raw }, Node)
  end
  return raw._node
end

-- Absorber-detection only, NOT a text-emptiness check: `is_empty()` answers
-- "is this the shared absorber standing in for a get_/attr miss", not "does
-- this node's text() happen to be """. A real node — even one whose own
-- text is "" (e.g. an element with only child elements, no direct text) —
-- is a real node and always returns false here. Check `:text() == ""`
-- separately if text-emptiness is what's actually wanted.
function Node:is_empty()
  return false
end

function Node:name()
  local raw = self.raw
  if raw._normName == nil then
    raw._normName = tagToVariable(raw.prefix .. raw.name)
  end
  return raw._normName
end

function Node:text()
  local raw = self.raw
  if raw._text == nil then
    local parts = {}
    for _, part in ipairs(raw.textparts) do
      if part.cdata then
        parts[#parts + 1] = "<![CDATA[" .. part.value .. "]]>"
      else
        parts[#parts + 1] = stripNonPrinting(part.value)
      end
    end
    raw._text = trim(table.concat(parts))
  end
  return raw._text
end

Node.__tostring = function(n)
  return n:text()
end

-- spec 2.3, "anything else" row: direct children only, name-matched,
-- fresh list, empty on miss. No text filter.
function Node:get(name)
  local result = {}
  for _, child in ipairs(self.raw.children) do
    local cn = wrap(child)
    if cn:name() == name then
      result[#result + 1] = cn
    end
  end
  return result
end

-- spec 2.3, "_" row: self-then-children pre-order, first match whose
-- normalised name matches AND whose text is non-empty.
local function findFirstMatch(raw, name)
  local n = wrap(raw)
  if n:name() == name and n:text() ~= "" then
    return raw
  end
  for _, child in ipairs(raw.children) do
    local match = findFirstMatch(child, name)
    if match then
      return match
    end
  end
  return nil
end

function Node:get_(name)
  local match = findFirstMatch(self.raw, name)
  if match then
    return wrap(match)
  end
  return ABSORBER
end

function Node:attr(name)
  return self.raw.attrmap[name]
end

local M = {}

function M.parse(xmlString)
  local root = xmlparser.parse(xmlString)
  return wrap(root)
end

-- Exposed for Task 7/8 and for testing the accent table directly.
M.tagToVariable = tagToVariable
M.absorber = ABSORBER

return M
