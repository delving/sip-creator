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

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Ensures the ACE record-definition refactored with elem-groups produces
 * an identical resolved tree to the original hand-written version.
 * <p>
 * Both files must resolve to the same structure: same elements, same
 * attributes, same nesting, same order. Any difference means the
 * refactoring changed semantics.
 */
class AceElemGroupRefactoringTest {

    private static final String ORIGINAL = "/recdef/ace-original.xml";
    private static final String REFACTORED = "/recdef/ace-refactored.xml";

    // ------------------------------------------------------------------
    // Tree serialisation — produces a canonical string from a resolved tree
    // ------------------------------------------------------------------

    /**
     * Serialises a RecDefNode tree into a human-readable, diff-friendly
     * canonical form.  Each node is one line with optional doc content:
     * <pre>
     *   ELEM test:root [doc: Label=Root Definition=A root element]
     *     ATTR @test:id
     *     ELEM test:child
     * </pre>
     * Two trees are identical (structure + documentation) iff their
     * serialisations are equal as strings.
     */
    private String serialise(RecDefNode node) {
        StringBuilder sb = new StringBuilder();
        serialise(node, 0, sb);
        return sb.toString();
    }

    private void serialise(RecDefNode node, int depth, StringBuilder sb) {
        String indent = "  ".repeat(depth);
        String kind = node.isAttr() ? "ATTR" : "ELEM";
        sb.append(indent).append(kind).append(' ').append(node.getTag());
        RecDef.Doc doc = node.getDoc();
        if (doc != null) {
            sb.append(" [doc:");
            if (doc.paraList != null) {
                for (RecDef.DocParagraph para : doc.paraList) {
                    sb.append(' ').append(para.name).append('=').append(para.content);
                }
            }
            if (doc.paras != null) {
                for (RecDef.DocParagraph para : doc.paras) {
                    sb.append(' ').append(para.name).append('=').append(para.content);
                }
            }
            if (doc.lines != null) {
                for (String line : doc.lines) {
                    sb.append(' ').append(line);
                }
            }
            sb.append(']');
        }
        sb.append('\n');
        for (RecDefNode child : node.getChildren()) {
            serialise(child, depth + 1, sb);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private RecDefTree loadTree(String resource) {
        InputStream stream = getClass().getResourceAsStream(resource);
        assertNotNull(stream, "Resource must exist: " + resource);
        RecDef recDef = RecDef.read(stream);
        return RecDefTree.create(recDef);
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    void originalAceRecDefLoadsSuccessfully() {
        RecDefTree tree = loadTree(ORIGINAL);
        assertNotNull(tree.getRoot());

        // Sanity: the root should have children (it's a non-trivial rec-def)
        List<RecDefNode> children = tree.getRoot().getChildren();
        assertNotNull(children);
        assert children.size() > 10 :
                "ACE rec-def root should have many child entities, got " + children.size();
    }

    @Test
    void refactoredAceRecDefLoadsSuccessfully() {
        RecDefTree tree = loadTree(REFACTORED);
        assertNotNull(tree.getRoot());

        List<RecDefNode> children = tree.getRoot().getChildren();
        assertNotNull(children);
        assert children.size() > 10 :
                "Refactored ACE rec-def root should have many child entities, got " + children.size();
    }

    @Test
    void refactoredTreeMatchesOriginal() {
        RecDefTree originalTree = loadTree(ORIGINAL);
        RecDefTree refactoredTree = loadTree(REFACTORED);

        String originalSer = serialise(originalTree.getRoot());
        String refactoredSer = serialise(refactoredTree.getRoot());

        if (!originalSer.equals(refactoredSer)) {
            // Find the first line that differs for a useful error message
            String[] origLines = originalSer.split("\n");
            String[] refLines = refactoredSer.split("\n");

            int maxLines = Math.max(origLines.length, refLines.length);
            for (int i = 0; i < maxLines; i++) {
                String origLine = i < origLines.length ? origLines[i] : "<missing>";
                String refLine = i < refLines.length ? refLines[i] : "<missing>";
                if (!origLine.equals(refLine)) {
                    fail(String.format(
                            "Trees differ at line %d:%n" +
                            "  original:   %s%n" +
                            "  refactored: %s%n" +
                            "  (original has %d lines, refactored has %d lines)",
                            i + 1, origLine, refLine,
                            origLines.length, refLines.length
                    ));
                }
            }
        }

        assertEquals(originalSer, refactoredSer,
                "Resolved trees must be structurally identical");
    }
}
