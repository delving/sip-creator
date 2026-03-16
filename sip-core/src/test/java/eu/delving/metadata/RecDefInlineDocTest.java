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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for inline documentation on elements, templates, and elem-groups.
 * Inline docs are defined as {@code <doc>} children inside {@code <elem>}
 * and should survive deep-copy through templates and elem-groups.
 * Path-based and tag-based docs from the {@code <docs>} section should
 * override inline docs.
 */
class RecDefInlineDocTest {

    private RecDefTree tree;
    private RecDefNode root;

    @BeforeEach
    void setUp() {
        InputStream stream = getClass().getResourceAsStream("/recdef/inline-docs-recdef.xml");
        assertNotNull(stream, "Test fixture recdef/inline-docs-recdef.xml must exist");
        RecDef recDef = RecDef.read(stream);
        tree = RecDefTree.create(recDef);
        root = tree.getRoot();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private RecDefNode childElem(RecDefNode parent, String tagString) {
        for (RecDefNode child : parent.getChildren()) {
            if (!child.isAttr() && child.getTag().toString().equals(tagString)) {
                return child;
            }
        }
        return null;
    }

    private String docPara(RecDefNode node, String paraName) {
        RecDef.Doc doc = node.getDoc();
        if (doc == null) return null;
        if (doc.paraList != null) {
            for (RecDef.DocParagraph para : doc.paraList) {
                if (paraName.equals(para.name)) return para.content;
            }
        }
        if (doc.paras != null) {
            for (RecDef.DocParagraph para : doc.paras) {
                if (paraName.equals(para.name)) return para.content;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Tests: inline doc on plain element
    // ------------------------------------------------------------------

    @Test
    void plainElemHasInlineDoc() {
        RecDefNode name = childElem(root, "test:name");
        assertNotNull(name, "name element should exist");
        assertNotNull(name.getDoc(), "name should have inline doc");
        assertEquals("The name of this thing", docPara(name, "Definition"));
    }

    // ------------------------------------------------------------------
    // Tests: inline doc on template elements survives deep-copy
    // ------------------------------------------------------------------

    @Test
    void templateElementCarriesInlineDoc() {
        RecDefNode hasId = childElem(root, "test:has-id");
        assertNotNull(hasId);

        RecDefNode identifier = childElem(hasId, "test:identifier");
        assertNotNull(identifier, "identifier template should be injected");
        assertNotNull(identifier.getDoc(),
                "inline doc on template element should survive deep-copy");
        assertEquals("A unique identifier for this entity",
                docPara(identifier, "Definition"));
    }

    @Test
    void templateChildElementCarriesInlineDoc() {
        RecDefNode hasId = childElem(root, "test:has-id");
        RecDefNode identifier = childElem(hasId, "test:identifier");
        RecDefNode value = childElem(identifier, "test:value");
        assertNotNull(value, "value child of template should exist");
        assertNotNull(value.getDoc(),
                "inline doc on template child should survive deep-copy");
        assertEquals("The string value of the identifier",
                docPara(value, "Definition"));
    }

    @Test
    void templateElementWithoutDocHasNullDoc() {
        RecDefNode hasId = childElem(root, "test:has-id");
        RecDefNode identifier = childElem(hasId, "test:identifier");
        RecDefNode type = childElem(identifier, "test:type");
        assertNotNull(type, "type child of template should exist");
        assertNull(type.getDoc(), "type has no inline doc and should be null");
    }

    // ------------------------------------------------------------------
    // Tests: inline doc on elem-group members survives deep-copy
    // ------------------------------------------------------------------

    @Test
    void elemGroupMemberCarriesInlineDoc() {
        RecDefNode concept = childElem(root, "test:concept");
        assertNotNull(concept);

        RecDefNode prefLabel = childElem(concept, "skos:prefLabel");
        assertNotNull(prefLabel, "prefLabel from labels group should exist");
        assertNotNull(prefLabel.getDoc(),
                "inline doc on elem-group member should survive deep-copy");
        assertEquals("The preferred label", docPara(prefLabel, "Definition"));
    }

    @Test
    void elemGroupSecondMemberCarriesInlineDoc() {
        RecDefNode concept = childElem(root, "test:concept");
        RecDefNode altLabel = childElem(concept, "skos:altLabel");
        assertNotNull(altLabel);
        assertNotNull(altLabel.getDoc());
        assertEquals("An alternative label", docPara(altLabel, "Definition"));
    }

    // ------------------------------------------------------------------
    // Tests: deep-copied docs are independent
    // ------------------------------------------------------------------

    @Test
    void templateDocsAreIndependentAcrossConsumers() {
        RecDefNode hasId = childElem(root, "test:has-id");
        RecDefNode alsoHasId = childElem(root, "test:also-has-id");

        RecDefNode id1 = childElem(hasId, "test:identifier");
        RecDefNode id2 = childElem(alsoHasId, "test:identifier");

        assertNotNull(id1.getDoc());
        assertNotNull(id2.getDoc());

        // Both should have the same content
        assertEquals(docPara(id1, "Definition"), docPara(id2, "Definition"));
    }

    // ------------------------------------------------------------------
    // Tests: path-based and tag-based docs override inline docs
    // ------------------------------------------------------------------

    @Test
    void pathBasedDocOverridesInlineDoc() {
        RecDefNode overridden = childElem(root, "test:overridden");
        assertNotNull(overridden);
        assertNotNull(overridden.getDoc());
        assertEquals("Path-based override", docPara(overridden, "Definition"),
                "Path-based doc from <docs> should override inline doc");
    }

    @Test
    void tagBasedDocOverridesInlineDoc() {
        RecDefNode tagged = childElem(root, "test:tagged");
        assertNotNull(tagged);
        assertNotNull(tagged.getDoc());
        assertEquals("Tag-based override", docPara(tagged, "Definition"),
                "Tag-based doc from <docs> should override inline doc");
    }
}
