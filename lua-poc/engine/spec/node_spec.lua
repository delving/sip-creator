-- Task 6: pure-Lua XML node navigation with GroovyNode semantics.
--
-- Contract under test (task-6-brief.md + docs/specs/mapping-language-core.md
-- sections 2 "Navigation" and 5 "Null propagation"):
--   node.parse(xml_string) -> root
--   n:get(name)  -> list of direct children matching normalised local name
--   n:get_(name) -> first non-empty-text descendant (self included,
--                   pre-order), or a shared "absorber" object on miss
--   n:attr(name) -> string, or nil on a real node's miss
--   n:text()     -> trimmed accumulated text
--   tostring(n)  == n:text()
--   absorber:get_/:attr/:text keep returning absorber/"" so chained
--   navigation never errors; absorber:is_empty() == true.

local node = require("node")

describe("node.parse / basic navigation (brief's required cases)", function()
  local xml = [[<record><title>  </title><title>Real</title><creator lang="en">X</creator></record>]]
  local root = node.parse(xml)

  it("counts direct children by local name", function()
    assert.equal(2, #root:get("title"))
  end)

  it("get_ skips empty-text matches and returns the first non-empty one", function()
    assert.equal("Real", root:get_("title"):text())
  end)

  it("attr reads a qualified attribute name", function()
    assert.equal("en", root:get_("creator"):attr("lang"))
  end)

  it("chains through a miss without erroring, yielding empty text", function()
    assert.equal("", root:get_("missing"):get_("deeper"):text())
  end)

  it("marks the absorber as empty", function()
    assert.is_true(root:get_("missing"):is_empty())
  end)
end)

describe("node:get (direct children, name-matched list)", function()
  local root = node.parse("<r><a>1</a><b>2</b><a>3</a></r>")

  it("preserves repeated siblings as separate entries, in document order", function()
    local as_ = root:get("a")
    assert.equal(2, #as_)
    assert.equal("1", as_[1]:text())
    assert.equal("3", as_[2]:text())
  end)

  it("returns an empty list (not nil, not a string) on a miss", function()
    local missing = root:get("nope")
    assert.are.same({}, missing)
    assert.equal(0, #missing)
  end)

  it("does not recurse into grandchildren", function()
    local outer = node.parse("<r><a><a>deep</a></a></r>")
    assert.equal(1, #outer:get("a"))
  end)
end)

describe("node:get_ (recursive, self-included, first non-empty-text match)", function()
  it("matches on self before descending", function()
    local r = node.parse("<r>top<a>nested</a></r>")
    assert.equal("top", r:get_("r"):text())
  end)

  it("finds a match nested below a non-matching child", function()
    local r = node.parse("<r><wrap><target>found</target></wrap></r>")
    assert.equal("found", r:get_("target"):text())
  end)

  it("real node attr() miss returns nil, not empty string", function()
    local r = node.parse("<r><a x=\"1\">v</a></r>")
    assert.is_nil(r:get_("a"):attr("y"))
  end)
end)

describe("namespace declarations are not attributes (MetadataParser.java:135-144)", function()
  -- SLAXML's SAX layer fires the attribute callback for xmlns="..." and
  -- xmlns:foo="..." the same as any ordinary attribute, but the real Java
  -- engine (StAX getAttributeCount/getAttributeName) never exposes
  -- namespace declarations that way. The whole golden corpus is
  -- namespaced (EDM/LIDO/MODS), so a leak here would put "xmlns" and
  -- "xmlns:<prefix>" into every element's attribute set.
  local r = node.parse(
    '<r xmlns="http://default/" xmlns:dc="http://purl.org/dc/elements/1.1/" dc:title="t" real="x"></r>'
  )

  it("does not expose the default-namespace declaration as an attribute", function()
    assert.is_nil(r:attr("xmlns"))
  end)

  it("does not expose a prefixed namespace declaration as an attribute", function()
    assert.is_nil(r:attr("xmlns:dc"))
  end)

  it("still exposes a real qualified attribute", function()
    assert.equal("t", r:attr("dc:title"))
  end)

  it("still exposes a real unprefixed attribute", function()
    assert.equal("x", r:attr("real"))
  end)

  it("keeps only the real attributes in the ordered attrs list", function()
    assert.equal(2, #r.raw.attrs)
  end)
end)

describe("absorber nil-absorption", function()
  local root = node.parse("<r></r>")
  local absorber = root:get_("nope")

  it("is a single shared object regardless of the miss path", function()
    local other = root:get_("also-nope")
    assert.equal(absorber, other)
  end)

  it("stringifies to empty string", function()
    assert.equal("", tostring(absorber))
  end)

  it("attr() on the absorber returns empty string, not nil", function()
    assert.equal("", absorber:attr("whatever"))
  end)

  it("keeps absorbing arbitrarily deep chains", function()
    assert.is_true(absorber:get_("a"):get_("b"):get_("c"):is_empty())
  end)

  it("a real node reports not empty", function()
    assert.is_false(root:is_empty())
  end)
end)

describe("tostring(n) == n:text()", function()
  it("matches for a real node", function()
    local r = node.parse("<r>  hello  </r>")
    assert.equal(r:text(), tostring(r))
    assert.equal("hello", tostring(r))
  end)
end)

describe("node name normalisation (tagToVariable, spec section 2.2)", function()
  it("concatenates prefix and local name with no separator", function()
    local r = node.parse('<record xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>t</dc:title></record>')
    assert.equal(1, #r:get("dctitle"))
  end)

  it("deletes -.;:_ from the normalised name", function()
    local r = node.parse("<r><some-tag>x</some-tag></r>")
    assert.equal(1, #r:get("sometag"))
  end)

  it("folds accented Latin characters to ASCII (UTF-8 input)", function()
    -- "café" -> "cafe" per StringUtil.tagToVariable's UNICODE/PLAIN_ASCII table.
    -- Exercised directly against tagToVariable, not through a parsed tag
    -- name: SLAXML's own tag-name pattern ("^<([%a_][%w_.-]*)") is
    -- ASCII-only (Lua patterns have no Unicode notion of %a/%w), so a
    -- non-ASCII byte in an element name fails to parse at the SAX layer
    -- before tagToVariable ever runs. That is a real limitation of the
    -- vendored parser for non-ASCII element/attribute *names* (values and
    -- text are unaffected) — see task-6-report.md.
    assert.equal("cafe", node.tagToVariable("caf\195\169"))
  end)
end)

describe("CDATA passthrough (spec section 2.1)", function()
  it("re-wraps CDATA as a literal marker inside text(), not unwrapped", function()
    local r = node.parse("<r><a><![CDATA[<b>raw</b>]]></a></r>")
    assert.equal("<![CDATA[<b>raw</b>]]>", r:get_("a"):text())
  end)
end)

describe("mixed text and elements are not reordered (R7)", function()
  it("keeps only element children in get(), and each child's own text separate", function()
    local r = node.parse("<r>before<a>A</a>between<b>B</b>after</r>")
    assert.equal(1, #r:get("a"))
    assert.equal(1, #r:get("b"))
    assert.equal("A", r:get_("a"):text())
    assert.equal("B", r:get_("b"):text())
  end)
end)
