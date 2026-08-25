-- Task 7: RDF/XML output builder.
--
-- Contract under test (task-7-brief.md + docs/specs/mapping-language-core.md
-- section 3 "Builder output"):
--   builder.new(namespaces) -> b
--   b:elem(qname, attrs, fn_or_text) -> el   -- nestable
--   b:to_rdfxml() -> string
--   empty-element stripping matches Utils.stripEmpty (Utils.java:70-94):
--     depth-first, removes whitespace-only text/CDATA and elements left
--     with neither children nor attributes; the root itself is exempt.
--
-- "Parses" is checked by round-tripping the emitted string back through
-- Task 6's own xml/node parser (no Jena available in this pure-Lua spec
-- environment) — a real XML parser accepting the output plus a structural
-- check on the result is the available proxy for "well-formed RDF/XML".

local builder = require("builder")
local stdlib = require("stdlib")
local node = require("node")

local NS = {
  rdf = "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
  edm = "http://www.europeana.eu/schemas/edm/",
  nave = "http://schemas.delving.eu/nave/terms/",
}

describe("builder.new / b:elem — basic 2-level document with an attribute (brief step 1)", function()
  it("builds a parseable document", function()
    local b = builder.new(NS)
    b:elem("rdf:RDF", nil, function()
      b:elem("edm:ProvidedCHO", { ["rdf:about"] = "http://example.org/1" })
    end)
    local xml = b:to_rdfxml()

    local parsed = node.parse(xml)
    assert.equal("rdfRDF", parsed:name())
    local cho = parsed:get("edmProvidedCHO")
    assert.equal(1, #cho)
    assert.equal("http://example.org/1", cho[1]:attr("rdf:about"))
  end)

  it("self-closes an element with an attribute but no content", function()
    local b = builder.new(NS)
    b:elem("rdf:RDF", nil, function()
      b:elem("edm:ProvidedCHO", { ["rdf:about"] = "http://example.org/1" })
    end)
    local xml = b:to_rdfxml()
    assert.is_not_nil(xml:find('<edm:ProvidedCHO rdf:about="http://example.org/1"/>', 1, true))
  end)

  it("emits a text-bearing element with plain content", function()
    local b = builder.new(NS)
    b:elem("rdf:RDF", nil, function()
      b:elem("edm:dataProvider", nil, "Huizer Museum")
    end)
    local parsed = node.parse(b:to_rdfxml())
    assert.equal("Huizer Museum", parsed:get_("edmdataProvider"):text())
  end)
end)

describe("empty-element stripping (Utils.java:70-94 semantics)", function()
  it("strips a child element with no text and no attributes", function()
    local b = builder.new(NS)
    b:elem("rdf:RDF", nil, function()
      b:elem("edm:Place", nil, nil)
      b:elem("edm:ProvidedCHO", { ["rdf:about"] = "http://x/1" })
    end)
    local xml = b:to_rdfxml()
    assert.is_nil(xml:find("edm:Place", 1, true))
    assert.is_not_nil(xml:find("edm:ProvidedCHO", 1, true))
  end)

  it("strips a child whose only content is whitespace", function()
    local b = builder.new(NS)
    b:elem("rdf:RDF", nil, function()
      b:elem("edm:Place", nil, "   \n  ")
    end)
    local xml = b:to_rdfxml()
    assert.is_nil(xml:find("edm:Place", 1, true))
  end)

  it("keeps an element that has an attribute even with no text (rdf:about-only case)", function()
    local b = builder.new(NS)
    b:elem("rdf:RDF", nil, function()
      b:elem("edm:ProvidedCHO", { ["rdf:about"] = "http://x/1" })
    end)
    local xml = b:to_rdfxml()
    assert.is_not_nil(xml:find("edm:ProvidedCHO", 1, true))
  end)

  it("stripping is depth-first: an empty grandchild empties its parent too", function()
    local b = builder.new(NS)
    b:elem("rdf:RDF", nil, function()
      b:elem("nave:Wrap", nil, function()
        b:elem("nave:Inner", nil, nil)
      end)
    end)
    local xml = b:to_rdfxml()
    assert.is_nil(xml:find("nave:Wrap", 1, true))
    assert.is_nil(xml:find("nave:Inner", 1, true))
  end)

  it("the root itself is never stripped, even with no surviving children", function()
    local b = builder.new(NS)
    b:elem("rdf:RDF", nil, function()
      b:elem("edm:Place", nil, nil)
    end)
    local xml = b:to_rdfxml()
    assert.is_not_nil(xml:find("<rdf:RDF", 1, true))
  end)
end)

describe("nested elements (nestable, document order)", function()
  it("preserves call order across siblings", function()
    local b = builder.new(NS)
    b:elem("rdf:RDF", nil, function()
      b:elem("edm:dataProvider", nil, "A")
      b:elem("edm:provider", nil, "B")
    end)
    local xml = b:to_rdfxml()
    local _, providerPos = xml:find("edm:dataProvider", 1, true)
    local secondPos = xml:find("edm:provider", providerPos, true)
    assert.is_not_nil(secondPos)
    local parsed = node.parse(xml)
    assert.equal("A", parsed:get_("edmdataProvider"):text())
    assert.equal("B", parsed:get_("edmprovider"):text())
  end)

  it("a deeply nested element round-trips", function()
    local b = builder.new(NS)
    b:elem("rdf:RDF", nil, function()
      b:elem("nave:DcnResource", nil, function()
        b:elem("nave:province", nil, "Noord-Holland")
      end)
    end)
    local parsed = node.parse(b:to_rdfxml())
    -- get_ on "naveDcnResource" would miss: that element's own direct
    -- text is "" (it holds only a child, no text of its own), and get_'s
    -- match rule requires non-empty text at the matching node itself
    -- (spec 2.3) — this is genuine, spec-faithful Task 6 behaviour, not a
    -- builder bug, so descend via get(...)[1] for the structural parent.
    assert.equal("Noord-Holland", parsed:get("naveDcnResource")[1]:get_("naveprovince"):text())
  end)
end)

describe("attribute value resolution (spec 3.3 resolveValue)", function()
  it("calls a function-valued attribute and uses its result", function()
    local b = builder.new(NS)
    b:elem("rdf:RDF", nil, function()
      b:elem("edm:ProvidedCHO", { ["rdf:about"] = function() return "http://computed/1" end })
    end)
    local parsed = node.parse(b:to_rdfxml())
    -- get_ (not get) would skip this: the element has an attribute but no
    -- text, and get_'s miss/match rule is text-based (spec 2.3).
    assert.equal("http://computed/1", parsed:get("edmProvidedCHO")[1]:attr("rdf:about"))
  end)

  it("skips an attribute whose resolved value is nil", function()
    local b = builder.new(NS)
    b:elem("rdf:RDF", nil, function()
      b:elem("edm:ProvidedCHO", { ["rdf:about"] = "http://x/1", ["rdf:resource"] = function() return nil end })
    end)
    local xml = b:to_rdfxml()
    assert.is_nil(xml:find("rdf:resource", 1, true))
  end)

  it("skips (suppresses) an attribute whose gstring value has only empty slots", function()
    local b = builder.new(NS)
    b:elem("rdf:RDF", nil, function()
      b:elem("edm:ProvidedCHO", {
        ["rdf:about"] = "http://x/1",
        ["nave:thumb"] = function() return stdlib.gs({ "http://img/", "" }, { "" }) end,
      })
    end)
    local xml = b:to_rdfxml()
    assert.is_nil(xml:find("nave:thumb", 1, true))
  end)

  it("keeps a gstring attribute whose slot is non-empty, rendered", function()
    local b = builder.new(NS)
    b:elem("rdf:RDF", nil, function()
      b:elem("edm:ProvidedCHO", {
        ["rdf:about"] = "http://x/1",
        ["nave:thumb"] = function() return stdlib.gs({ "http://img/", "" }, { "abc" }) end,
      })
    end)
    local parsed = node.parse(b:to_rdfxml())
    assert.equal("http://img/abc", parsed:get("edmProvidedCHO")[1]:attr("nave:thumb"))
  end)

  it("rejects an xmlns attribute (DOMBuilder.java:216-219)", function()
    local b = builder.new(NS)
    assert.has_error(function()
      b:elem("rdf:RDF", nil, function()
        b:elem("edm:ProvidedCHO", { ["xmlns:foo"] = "http://x/" })
      end)
    end)
  end)
end)

describe("content resolution: gstring suppression on element body (spec 3.4, hazard a)", function()
  it("a plain string body is never suppressed even if it renders empty-ish", function()
    local b = builder.new(NS)
    b:elem("rdf:RDF", nil, function()
      b:elem("edm:dataProvider", { ["rdf:about"] = "http://keep/1" }, "")
    end)
    -- kept because it has an attribute regardless of empty text; the point
    -- here is that passing "" directly does not raise/short-circuit like a
    -- suppressed gstring would.
    local xml = b:to_rdfxml()
    assert.is_not_nil(xml:find("edm:dataProvider", 1, true))
  end)

  it("a suppressed gstring body yields no text, so the element strips if it also has no attrs", function()
    local b = builder.new(NS)
    b:elem("rdf:RDF", nil, function()
      b:elem("edm:dataProvider", nil, function()
        return stdlib.gs({ "https://example.com/", "" }, { "" })
      end)
    end)
    local xml = b:to_rdfxml()
    assert.is_nil(xml:find("edm:dataProvider", 1, true))
  end)

  it("a non-suppressed gstring body renders its text", function()
    local b = builder.new(NS)
    b:elem("rdf:RDF", nil, function()
      b:elem("edm:dataProvider", nil, function()
        return stdlib.gs({ "https://example.com/", "" }, { "slug" })
      end)
    end)
    local parsed = node.parse(b:to_rdfxml())
    assert.equal("https://example.com/slug", parsed:get_("edmdataProvider"):text())
  end)
end)

describe("CDATA passthrough round-trip (spec 3.5 toTextNodes / section 2.1)", function()
  it("re-emits a literal CDATA marker as a real CDATA section that re-parses the same way", function()
    local b = builder.new(NS)
    b:elem("rdf:RDF", nil, function()
      b:elem("edm:rawHtml", nil, "<![CDATA[<b>raw</b>]]>")
    end)
    local xml = b:to_rdfxml()
    assert.is_not_nil(xml:find("<![CDATA[<b>raw</b>]]>", 1, true))
    local parsed = node.parse(xml)
    assert.equal("<![CDATA[<b>raw</b>]]>", parsed:get_("edmrawHtml"):text())
  end)
end)

describe("xml:lang (spec 3.5): only set when the element has non-empty text", function()
  it("drops xml:lang on an otherwise-empty element", function()
    local b = builder.new(NS)
    b:elem("rdf:RDF", nil, function()
      b:elem("edm:Place", { ["xml:lang"] = "en" }, nil)
    end)
    local xml = b:to_rdfxml()
    assert.is_nil(xml:find("xml:lang", 1, true))
  end)

  it("keeps xml:lang when the element has non-empty text", function()
    local b = builder.new(NS)
    b:elem("rdf:RDF", nil, function()
      b:elem("edm:title", { ["xml:lang"] = "en" }, "Hello")
    end)
    local parsed = node.parse(b:to_rdfxml())
    assert.equal("en", parsed:get_("edmtitle"):attr("xml:lang"))
  end)
end)

describe("text escaping", function()
  it("escapes &, <, > in text content", function()
    local b = builder.new(NS)
    b:elem("rdf:RDF", nil, function()
      b:elem("edm:dataProvider", nil, "A & B < C > D")
    end)
    local parsed = node.parse(b:to_rdfxml())
    assert.equal("A & B < C > D", parsed:get_("edmdataProvider"):text())
  end)

  it("escapes double-quotes in attribute values", function()
    local b = builder.new(NS)
    b:elem("rdf:RDF", nil, function()
      b:elem("edm:ProvidedCHO", { ["rdf:about"] = 'has "quotes"' })
    end)
    local parsed = node.parse(b:to_rdfxml())
    assert.equal('has "quotes"', parsed:get("edmProvidedCHO")[1]:attr("rdf:about"))
  end)
end)
