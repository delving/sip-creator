--[==[
builder.lua — RDF/XML output builder (Task 7 of the Lua mapping-engine
feasibility project).

Implements the T1-relevant subset of "Builder output"
(docs/specs/mapping-language-core.md section 3): nestable element
construction, closure/gstring value resolution (section 3.3/3.4), CDATA
passthrough (section 3.5, `DOMBuilder.toTextNodes`), the `xml:lang`
only-when-non-empty-text rule (section 3.5), and empty-element stripping
matching `Utils.stripEmpty` exactly (Utils.java:70-94).

Surface (task-7-brief.md):
  builder.new(namespaces) -> b        -- namespaces: { prefix = uri, ... }
  b:elem(qname, attrs, fn_or_text) -> el   -- nestable
  b:to_rdfxml() -> string

`attrs` is a plain `{ qname = value }` table; each value is resolved via
the same rule as body content (spec 3.3 `resolveValue`): a function is
called and its result resolved recursively; a `stdlib.gs(...)` gstring
table is reduced to its text or dropped (nil) if `suppressed`; anything
else passes through. A value that resolves to nil is a skipped attribute
(spec 3.5, DOMBuilder.java:270), never an empty `attr=""`.

`fn_or_text` (the element's content) may be:
  - nil                         -> no content
  - a plain Lua string          -> text content, NEVER suppressed (spec
                                    3.4: only a GString/gstring is subject
                                    to suppressIfAllVariablesEmpty)
  - a function                  -> called with `(b, el)`; nested `b:elem`
                                    calls inside it attach children to
                                    `el` (this is what makes elem
                                    "nestable"); the function's return
                                    value, if any, is resolved the same
                                    way as a plain content value and
                                    becomes `el`'s own text

Verification note: golden comparison (Task 5's GoldenVerify) is a Jena
RDF-graph isomorphism check, not a byte-diff, so this module does not
attempt to reproduce the reference implementation's exact
attribute/namespace-declaration ORDER (spec 3.1's builder-call bookkeeping
is an implementation detail of how DOMBuilder reconstructs nesting from a
flat recording; the observable contract is document order for elements,
which this module produces directly by attaching children as they are
built). Attributes are serialized in a fixed (sorted) order for
determinism, since XML attribute order is not semantically significant
and Lua tables have no reliable insertion order to preserve anyway.

Deferred (out of scope for this task; see task-7-report.md):
  - Element multiplication (`calcElementsRequired`/`extractValue`, spec
    3.5): a single `b:elem` call produces exactly one element. Task 8's
    generated code is expected to unroll list/loop-valued mappings into
    repeated `b:elem` calls itself, not rely on the builder to multiply
    from a list-valued attribute or content value.
  - `xml:lang` BCP-47 validation (`LanguageTagException`, spec 3.5): only
    the "set the attribute only when the element has non-empty text" half
    of the rule is implemented; malformed/empty tag values are not
    rejected.
  - Root `xsi:schemaLocation` injection (spec 3.5): requires rec-def
    schema metadata this module is never given.
  - "No namespace for <prefix>" enforcement (spec 3.5): an undeclared
    prefix is emitted as written rather than raising.
]==]

local stdlib = require("stdlib")

local Builder = {}
Builder.__index = Builder

local M = {}

function M.new(namespaces)
  return setmetatable({
    namespaces = namespaces or {},
    stack = {},
    root = nil,
  }, Builder)
end

-- spec 3.3 resolveValue: a function is called (and its result resolved
-- recursively); a gstring table is reduced to its text, or dropped (nil)
-- if `suppressed`; an element table already attached to its parent's
-- content (returned from a nested b:elem call) contributes no text of its
-- own; anything else (nil, a plain string, a number/boolean) passes
-- through unchanged.
function Builder:resolve(v)
  if v == nil then
    return nil
  end
  if type(v) == "function" then
    return self:resolve(v(self))
  end
  if type(v) == "table" then
    if v.__gstring then
      if v.suppressed then
        return nil
      end
      return v.text
    end
    if v.qname then
      -- an element already attached to its parent during its own :elem
      -- call; it is structural content, not text (spec 3.5: "Only
      -- String/GString content becomes text ... Node content is attached
      -- structurally instead").
      return nil
    end
  end
  return v
end

-- spec 3.5: "xmlns and xmlns:* attributes are rejected outright"
-- (DOMBuilder.java:216-219).
local function reject_xmlns(name)
  if name == "xmlns" or name:sub(1, 6) == "xmlns:" then
    error("builder: xmlns/xmlns:* attributes are rejected (DOMBuilder.java:216-219): " .. name, 3)
  end
end

function Builder:_attr_keys(attrs)
  local keys = {}
  if attrs then
    for k in pairs(attrs) do
      keys[#keys + 1] = k
    end
    table.sort(keys)
  end
  return keys
end

-- Splits rendered text at literal "<![CDATA[" / "]]>" markers (as
-- produced by node.lua's :text(), spec 2.1) into alternating plain-text
-- and CDATA segments, matching `DOMBuilder.toTextNodes`
-- (DOMBuilder.java:371-390): each segment becomes its own content item so
-- Utils.stripEmpty-equivalent stripping (below) can test each one's
-- trimmed emptiness independently, exactly as it would for real
-- TEXT_NODE/CDATA_SECTION_NODE DOM nodes. An unterminated "<![CDATA["
-- raises, matching DOMBuilder's "No CDATA terminator".
local function split_text_cdata(text)
  local segments = {}
  local pos = 1
  local len = #text
  while pos <= len do
    local s = text:find("<![CDATA[", pos, true)
    if not s then
      segments[#segments + 1] = { cdata = false, value = text:sub(pos) }
      break
    end
    if s > pos then
      segments[#segments + 1] = { cdata = false, value = text:sub(pos, s - 1) }
    end
    local contentStart = s + 9 -- #"<![CDATA[" == 9
    local closeS, closeE = text:find("]]>", contentStart, true)
    if not closeS then
      error("builder: No CDATA terminator", 2)
    end
    segments[#segments + 1] = { cdata = true, value = text:sub(contentStart, closeS - 1) }
    pos = closeE + 1
  end
  if #segments == 0 then
    segments[#segments + 1] = { cdata = false, value = "" }
  end
  return segments
end

function Builder:elem(qname, attrs, fn_or_text)
  local el = { qname = qname, attrs = {}, attr_order = {}, content = {} }

  local parent = self.stack[#self.stack]
  if parent then
    table.insert(parent.content, { el = el })
  elseif self.root then
    error("builder: a document has exactly one root element (got a second top-level b:elem call for '" .. qname .. "')", 2)
  else
    self.root = el
  end

  for _, name in ipairs(self:_attr_keys(attrs)) do
    reject_xmlns(name)
    local resolved = self:resolve(attrs[name])
    if resolved ~= nil then
      el.attrs[name] = tostring(resolved)
      table.insert(el.attr_order, name)
    end
  end

  if fn_or_text ~= nil then
    local resolved
    if type(fn_or_text) == "function" then
      table.insert(self.stack, el)
      local ok, ret = pcall(fn_or_text, self, el)
      table.remove(self.stack)
      if not ok then
        error(ret, 0)
      end
      resolved = self:resolve(ret)
    else
      resolved = self:resolve(fn_or_text)
    end
    if resolved ~= nil then
      for _, seg in ipairs(split_text_cdata(tostring(resolved))) do
        table.insert(el.content, seg)
      end
    end
  end

  -- spec 3.5: xml:lang is only actually set when the element has
  -- non-empty text content, evaluated at this same call (attributes and
  -- body are resolved together here, matching the reference's per-call
  -- timing).
  if el.attrs["xml:lang"] and not self:_has_nonempty_text(el) then
    el.attrs["xml:lang"] = nil
    local order = {}
    for _, n in ipairs(el.attr_order) do
      if n ~= "xml:lang" then
        order[#order + 1] = n
      end
    end
    el.attr_order = order
  end

  return el
end

function Builder:_has_nonempty_text(el)
  for _, item in ipairs(el.content) do
    if not item.el and item.value:match("%S") then
      return true
    end
  end
  return false
end

-- Utils.stripEmpty (Utils.java:70-94): depth-first; removes whitespace-only
-- text/CDATA content items, and child elements left with neither content
-- nor attributes after their own stripping. The root itself is exempt —
-- Utils.stripEmptyElements only ever recurses into a node's CHILDREN
-- (Utils.java:43-47), it never asks whether the root itself is empty.
local function strip_empty(el)
  local kept = {}
  for _, item in ipairs(el.content) do
    if item.el then
      strip_empty(item.el)
      local child = item.el
      local has_attrs = next(child.attrs) ~= nil
      if #child.content > 0 or has_attrs then
        kept[#kept + 1] = item
      end
    else
      if item.value:match("%S") then
        kept[#kept + 1] = item
      end
    end
  end
  el.content = kept
end

local function esc_text(s)
  s = s:gsub("&", "&amp;")
  s = s:gsub("<", "&lt;")
  s = s:gsub(">", "&gt;")
  return s
end

local function esc_attr(s)
  s = esc_text(s)
  s = s:gsub('"', "&quot;")
  return s
end

function Builder:_serialize(el, out, is_root)
  out[#out + 1] = "<" .. el.qname
  if is_root then
    local prefixes = {}
    for prefix in pairs(self.namespaces) do
      prefixes[#prefixes + 1] = prefix
    end
    table.sort(prefixes)
    for _, prefix in ipairs(prefixes) do
      out[#out + 1] = ' xmlns:' .. prefix .. '="' .. esc_attr(self.namespaces[prefix]) .. '"'
    end
  end
  for _, name in ipairs(el.attr_order) do
    local value = el.attrs[name]
    if value ~= nil then
      out[#out + 1] = " " .. name .. '="' .. esc_attr(value) .. '"'
    end
  end
  if #el.content == 0 then
    out[#out + 1] = "/>"
    return
  end
  out[#out + 1] = ">"
  for _, item in ipairs(el.content) do
    if item.el then
      self:_serialize(item.el, out, false)
    elseif item.cdata then
      out[#out + 1] = "<![CDATA[" .. item.value .. "]]>"
    else
      out[#out + 1] = esc_text(item.value)
    end
  end
  out[#out + 1] = "</" .. el.qname .. ">"
end

function Builder:to_rdfxml()
  if not self.root then
    error("builder: no root element (call b:elem at least once before b:to_rdfxml)", 2)
  end
  strip_empty(self.root)
  local out = { "<?xml version='1.0' encoding='UTF-8'?>\n" }
  self:_serialize(self.root, out, true)
  return table.concat(out)
end

return M
