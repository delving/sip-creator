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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for elem-group resolution in RecDef, including interaction with
 * templates (target attribute) and inline subelements.
 */
class RecDefElemGroupTest {

    private RecDefTree tree;
    private RecDefNode root;

    @BeforeEach
    void setUp() {
        InputStream stream = getClass().getResourceAsStream("/recdef/elem-groups-recdef.xml");
        assertNotNull(stream, "Test fixture recdef/elem-groups-recdef.xml must exist");
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

    private RecDefNode childAttr(RecDefNode parent, String tagString) {
        for (RecDefNode child : parent.getChildren()) {
            if (child.isAttr() && child.getTag().toString().equals(tagString)) {
                return child;
            }
        }
        return null;
    }

    private List<String> elemChildTags(RecDefNode parent) {
        return parent.getChildren().stream()
                .filter(n -> !n.isAttr())
                .map(n -> n.getTag().toString())
                .collect(Collectors.toList());
    }

    private List<String> attrChildTags(RecDefNode parent) {
        return parent.getChildren().stream()
                .filter(RecDefNode::isAttr)
                .map(n -> n.getTag().toString())
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------------------
    // Tests: simple elem-group ("labels")
    // ------------------------------------------------------------------

    @Test
    void simpleElemGroupResolvesChildElements() {
        RecDefNode concept = childElem(root, "test:concept");
        assertNotNull(concept, "concept element should exist");

        List<String> elems = elemChildTags(concept);
        assertEquals(2, elems.size(), "labels group should inject 2 elements");
        assertEquals("skos:prefLabel", elems.get(0));
        assertEquals("skos:altLabel", elems.get(1));
    }

    @Test
    void simpleElemGroupElementsResolveTheirAttrs() {
        RecDefNode concept = childElem(root, "test:concept");
        RecDefNode prefLabel = childElem(concept, "skos:prefLabel");
        assertNotNull(prefLabel, "skos:prefLabel should exist under concept");

        List<String> attrs = attrChildTags(prefLabel);
        assertTrue(attrs.contains("xml:lang"),
                "skos:prefLabel should have xml:lang attribute from attrs reference");
    }

    // ------------------------------------------------------------------
    // Tests: elem-group with templates ("core-entity")
    // ------------------------------------------------------------------

    @Test
    void elemGroupWithTemplateTargetResolvesNestedSubtree() {
        RecDefNode thing = childElem(root, "test:thing");
        assertNotNull(thing, "thing element should exist");

        List<String> elems = elemChildTags(thing);
        assertEquals(2, elems.size(), "core-entity group should inject 2 elements");
        assertEquals("test:has-id", elems.get(0));
        assertEquals("test:has-type", elems.get(1));

        // has-id should have the template injected as a child element via target="identifier"
        // (templates inject the whole element, not just its children)
        RecDefNode hasId = childElem(thing, "test:has-id");
        assertNotNull(hasId);

        List<String> hasIdChildren = elemChildTags(hasId);
        assertEquals(1, hasIdChildren.size(),
                "has-id should have the template element (identifier) as a child");
        assertEquals("test:identifier", hasIdChildren.get(0));

        // The template element itself should contain the leaf children
        RecDefNode identifier = childElem(hasId, "test:identifier");
        assertNotNull(identifier, "identifier template element should be present");

        List<String> identifierChildren = elemChildTags(identifier);
        assertEquals(2, identifierChildren.size(),
                "identifier template should have its own children (value, type)");
        assertEquals("test:value", identifierChildren.get(0));
        assertEquals("test:type", identifierChildren.get(1));
    }

    @Test
    void templateChildrenInsideElemGroupResolveTheirOwnAttrs() {
        RecDefNode thing = childElem(root, "test:thing");
        RecDefNode hasId = childElem(thing, "test:has-id");
        RecDefNode identifier = childElem(hasId, "test:identifier");
        assertNotNull(identifier, "identifier template element should exist");

        RecDefNode value = childElem(identifier, "test:value");
        assertNotNull(value, "value element from template should exist");

        List<String> valueAttrs = attrChildTags(value);
        assertTrue(valueAttrs.contains("xml:lang"),
                "value element from template should have xml:lang resolved");
    }

    @Test
    void elemGroupElementsOwnAttrsResolved() {
        RecDefNode thing = childElem(root, "test:thing");
        RecDefNode hasId = childElem(thing, "test:has-id");
        assertNotNull(hasId);

        List<String> hasIdAttrs = attrChildTags(hasId);
        assertTrue(hasIdAttrs.contains("test:ref"),
                "has-id should have its own 'ref' attribute resolved");
    }

    // ------------------------------------------------------------------
    // Tests: larger elem-group ("labeled-entity")
    // ------------------------------------------------------------------

    @Test
    void labeledEntityGroupResolvesAllFourChildren() {
        RecDefNode agent = childElem(root, "test:agent");
        assertNotNull(agent, "agent element should exist");

        List<String> elems = elemChildTags(agent);
        assertEquals(4, elems.size(),
                "labeled-entity group should inject has-id, prefLabel, altLabel, has-type");
        assertEquals("test:has-id", elems.get(0));
        assertEquals("skos:prefLabel", elems.get(1));
        assertEquals("skos:altLabel", elems.get(2));
        assertEquals("test:has-type", elems.get(3));
    }

    @Test
    void labeledEntityTemplateTargetResolvedRecursively() {
        RecDefNode agent = childElem(root, "test:agent");
        RecDefNode hasId = childElem(agent, "test:has-id");
        assertNotNull(hasId);

        // The template element (identifier) should be injected as a child
        List<String> hasIdChildren = elemChildTags(hasId);
        assertEquals(1, hasIdChildren.size(),
                "has-id under agent should have the template element as child");
        assertEquals("test:identifier", hasIdChildren.get(0));

        // And the template element should contain its own children
        RecDefNode identifier = childElem(hasId, "test:identifier");
        List<String> subtree = elemChildTags(identifier);
        assertEquals(2, subtree.size(), "identifier template should have value and type");
        assertEquals("test:value", subtree.get(0));
        assertEquals("test:type", subtree.get(1));
    }

    // ------------------------------------------------------------------
    // Tests: elem-group combined with inline subelements
    // ------------------------------------------------------------------

    @Test
    void elemGroupCombinedWithInlineSubelements() {
        RecDefNode place = childElem(root, "test:place");
        assertNotNull(place, "place element should exist");

        List<String> elems = elemChildTags(place);
        // elem-groups are added before inline subelements in resolve()
        assertEquals(4, elems.size(),
                "place should have 2 from labels group + 2 inline (lat, lon)");
        assertEquals("skos:prefLabel", elems.get(0));
        assertEquals("skos:altLabel", elems.get(1));
        assertEquals("test:lat", elems.get(2));
        assertEquals("test:lon", elems.get(3));
    }

    // ------------------------------------------------------------------
    // Tests: elem-groups produce independent deep copies
    // ------------------------------------------------------------------

    @Test
    void elemGroupElementsAreDeepCopiedAcrossConsumers() {
        // Both concept and place use the "labels" group — they should get
        // independent copies, not shared instances
        RecDefNode concept = childElem(root, "test:concept");
        RecDefNode place = childElem(root, "test:place");

        RecDefNode conceptPrefLabel = childElem(concept, "skos:prefLabel");
        RecDefNode placePrefLabel = childElem(place, "skos:prefLabel");

        assertNotNull(conceptPrefLabel);
        assertNotNull(placePrefLabel);

        // The paths should differ because they live under different parents
        assertNotNull(conceptPrefLabel.getPath());
        assertNotNull(placePrefLabel.getPath());
        assertTrue(!conceptPrefLabel.getPath().equals(placePrefLabel.getPath()),
                "Deep-copied elements under different parents should have different paths");
    }

    // ------------------------------------------------------------------
    // Tests: elem without elem-group (sanity check)
    // ------------------------------------------------------------------

    @Test
    void elementWithoutElemGroupResolvesNormally() {
        RecDefNode plain = childElem(root, "test:plain");
        assertNotNull(plain, "plain element should exist");

        List<String> elems = elemChildTags(plain);
        assertEquals(1, elems.size());
        assertEquals("test:name", elems.get(0));
    }

    // ------------------------------------------------------------------
    // Tests: elemGroups field is nulled after resolution (bug fix)
    // ------------------------------------------------------------------

    @Test
    void elemGroupsFieldNulledAfterResolution() {
        // After resolution, the elemGroups string field on Elem should be null
        // (this was the bug: previously attrGroups was nulled instead)
        RecDef recDef = tree.getRecDef();
        for (RecDef.Elem elem : recDef.root.elemList) {
            assertEquals(null, elem.elemGroups,
                    "elemGroups on " + elem.tag + " should be null after resolution");
        }
    }
}
