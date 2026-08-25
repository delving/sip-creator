--[[
xml.lua — thin tree builder on top of the vendored SLAXML SAX parser.

Produces a raw tree shaped for the GroovyNode adapter in node.lua, not
slaxdom's generic DOM shape (see vendor/slaxml.lua's header for why
slaxdom.lua was not also vendored). Each element node is:

  {
    name     = "title",           -- local name, as parsed
    prefix   = "dc" or "",        -- namespace prefix, "" if none
    uri      = "http://..." or nil,
    attrs    = { {name=, value=, prefix=, uri=, qname=}, ... },  -- document order
    attrmap  = { ["lang"] = "en", ["xml:lang"] = "en", ... },     -- qname -> value
    children = { <element>, ... },  -- ELEMENT children only, document order
    textparts = { {cdata=false, value="..."}, {cdata=true, value="..."}, ... },
    parent   = <element> or nil,
  }

This mirrors GroovyNode/MetadataParser: text and CDATA are accumulated
onto the enclosing element (never turned into child nodes of their own);
`children` holds only elements, in document order (mapping-language-core.md
section 2.1).
--]]

local slaxml = require("vendor.slaxml")

local M = {}

local function newElement(parent, name, uri, prefix)
  return {
    name = name,
    prefix = prefix or "",
    uri = uri,
    attrs = {},
    attrmap = {},
    children = {},
    textparts = {},
    parent = parent,
  }
end

function M.parse(xmlString)
  local root = nil
  local stack = {}
  local top = nil

  local callbacks = {}

  function callbacks.startElement(name, uri, prefix)
    local el = newElement(top, name, uri, prefix)
    if top then
      table.insert(top.children, el)
    end
    if not root then
      root = el
    end
    table.insert(stack, el)
    top = el
  end

  function callbacks.attribute(name, value, uri, prefix)
    local qname = name
    if prefix then
      qname = prefix .. ":" .. name
    end
    table.insert(top.attrs, { name = name, value = value, prefix = prefix, uri = uri, qname = qname })
    top.attrmap[qname] = value
  end

  function callbacks.text(text, cdata)
    if top then
      table.insert(top.textparts, { cdata = cdata, value = text })
    end
  end

  function callbacks.closeElement(_name, _uri, _prefix)
    table.remove(stack)
    top = stack[#stack]
  end

  -- pi/comment callbacks intentionally omitted: PIs and comments are
  -- dropped, matching MetadataParser which never turns them into nodes.

  local parser = slaxml:parser(callbacks)
  parser:parse(xmlString, { stripWhitespace = false })

  if not root then
    error("xml.parse: no root element found")
  end
  return root
end

return M
