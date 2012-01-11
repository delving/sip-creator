/*
 * Copyright 2010 DELVING BV
 *
 *  Licensed under the EUPL, Version 1.0 or? as soon they
 *  will be approved by the European Commission - subsequent
 *  versions of the EUPL (the "Licence");
 *  you may not use this work except in compliance with the
 *  Licence.
 *  You may obtain a copy of the Licence at:
 *
 *  http://ec.europa.eu/idabc/eupl
 *
 *  Unless required by applicable law or agreed to in
 *  writing, software distributed under the Licence is
 *  distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied.
 *  See the Licence for the specific language governing
 *  permissions and limitations under the Licence.
 */

package eu.delving.sip.xml;

import eu.delving.metadata.Path;
import eu.delving.metadata.RecDef;
import eu.delving.metadata.RecDefNode;
import eu.delving.sip.base.HtmlPanel;
import eu.delving.sip.base.Utility;
import eu.delving.sip.model.BookmarksTreeModel;
import eu.delving.sip.model.RecDefTreeNode;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.io.IOException;

/**
 * Putting the rec def into a JTree
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class RecDefViewer extends JFrame {
    private static final int MARGIN = 10;
    private HtmlPanel recDefPanel = new HtmlPanel("Details");
    private HtmlPanel bookmarkPanel = new HtmlPanel("Details");
    private JTree recDefTree, bookmarksTree;

    public RecDefViewer(RecDef recDef) {
        super("RecDef Viewer");
        recDefTree = new JTree(new DefaultTreeModel(RecDefTreeNode.create(RecDefNode.create(null, recDef))));
        recDefTree.setCellRenderer(new RecDefTreeNode.Renderer());
        recDefTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        recDefTree.getSelectionModel().addTreeSelectionListener(new RecDefSelection());
        recDefTree.setSelectionRow(0);
        bookmarksTree = new JTree(new BookmarksTreeModel(recDef.bookmarks));
        bookmarksTree.setRootVisible(false);
        bookmarksTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        bookmarksTree.getSelectionModel().addTreeSelectionListener(new BookmarkSelection());
        bookmarksTree.setCellRenderer(new BookmarkRenderer());
//        new DropTarget(recDefTree, new DropTargetListener() {
//            @Override
//            public void dragEnter(DropTargetDragEvent dropTargetDragEvent) {
//            }
//
//            @Override
//            public void dragOver(DropTargetDragEvent dropTargetDragEvent) {
//            }
//
//            @Override
//            public void dropActionChanged(DropTargetDragEvent dropTargetDragEvent) {
//            }
//
//            @Override
//            public void dragExit(DropTargetEvent dropTargetEvent) {
//            }
//
//            @Override
//            public void drop(DropTargetDropEvent event) {
//                try {
//                    event.getDropTargetContext().getDropTarget().
//                    event.getTransferable().getTransferData(FLAVOR);
//                }
//                catch (Exception e) {
//                    e.printStackTrace();  // todo: something
//                }
//            }
//        })
        fill(getContentPane());
        setSize(Toolkit.getDefaultToolkit().getScreenSize());
    }

    private void fill(Container content) {
        content.add(createRecDefPanel(), BorderLayout.CENTER);
    }

    private JPanel createRecDefPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createEmptyBorder(MARGIN, MARGIN, MARGIN, MARGIN));
        p.add(createRecDefTreePanel(), BorderLayout.CENTER);
        p.add(createBookmarksTreePanel(), BorderLayout.SOUTH);
        return p;
    }

    private JPanel createBookmarksTreePanel() {
        JPanel p = new JPanel(new GridLayout(1, 0));
        p.setBorder(BorderFactory.createTitledBorder("Bookmarks"));
        p.add(new JScrollPane(bookmarksTree));
        p.add(bookmarkPanel);
        return p;
    }

    private JPanel createRecDefTreePanel() {
        JPanel p = new JPanel(new GridLayout(1, 0));
        p.setBorder(BorderFactory.createTitledBorder("Record Definition"));
        p.add(new JScrollPane(recDefTree));
        p.add(recDefPanel);
        return p;
    }

    private class RecDefSelection implements TreeSelectionListener {

        @Override
        public void valueChanged(TreeSelectionEvent event) {
            RecDefTreeNode node = (RecDefTreeNode) event.getPath().getLastPathComponent();
            showNode(node.getRecDefNode());
            RecDefTreeNode root = (RecDefTreeNode) recDefTree.getModel().getRoot();
            root.showPath(recDefTree, node.getRecDefPath().getTagPath());
        }

    }

    private void showNode(RecDefNode node) {
        recDefPanel
                .setTemplate(node.isAttr() ? "recdef-attribute" : "recdef-element")
                .put("name", node.getTag())
                .put("doc", node.getDoc())
                .put("options", node.getOptions())
                .put("node", null) // todo: node.getNode())
                .render();
    }

    private class BookmarkSelection implements TreeSelectionListener {

        @Override
        public void valueChanged(TreeSelectionEvent event) {
            Object object = event.getPath().getLastPathComponent();
            if (object instanceof RecDef.Category) {
                RecDef.Category category = (RecDef.Category) object;
                bookmarkPanel
                        .setTemplate("bookmark-category")
                        .put("name", category.name)
                        .put("doc", category.doc)
                        .render();
                if (bookmarksTree.isCollapsed(event.getPath())) {
                    bookmarksTree.expandPath(event.getPath());
                }
                else {
                    bookmarksTree.collapsePath(event.getPath());
                }
            }
            else if (object instanceof RecDef.Ref) {
                RecDef.Ref ref = (RecDef.Ref) object;
                TreePath path = getTreePath(ref.path, recDefTree.getModel());
                recDefTree.setSelectionPath(path);
                bookmarkPanel
                        .setTemplate(ref.isAttr() ? "bookmark-attribute" : "bookmark-element")
                        .put("name", ref.display)
                        .put("doc", ref.doc)
                        .put("options", ref.options).
                        render();
            }
        }

        private TreePath getTreePath(Path path, TreeModel model) {
            return getTreePath(path, (RecDefTreeNode) model.getRoot());
        }

        private TreePath getTreePath(Path path, RecDefTreeNode node) {
            if (node.getRecDefPath().getTagPath().equals(path)) {
                return node.getRecDefPath();
            }
            for (RecDefTreeNode sub : node.getChildren()) {
                TreePath subPath = getTreePath(path, sub);
                if (subPath != null) return subPath;
            }
            return null;
        }
    }

    private static class BookmarkRenderer extends DefaultTreeCellRenderer {

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            Component component = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            if (value instanceof RecDef.Category) {
                setIcon(expanded ? Utility.BOOKMARK_EXPANDED_ICON : Utility.BOOKMARK_ICON);
            }
            else if (value instanceof RecDef.Ref) {
                RecDef.Ref ref = (RecDef.Ref) value;
                if (ref.isAttr()) {
                    setIcon(Utility.ATTRIBUTE_ICON);
                }
                else if (!ref.elem.elemList.isEmpty()) {
                    setIcon(Utility.COMPOSITE_ELEMENT_ICON);
                }
                else {
                    setIcon(Utility.VALUE_ELEMENT_ICON);
                }
            }
            return component;
        }
    }

    public static void main(String[] args) throws IOException {
        RecDef recDef = RecDef.read(RecDefViewer.class.getResource("/lido-recdef.xml").openStream());
        RecDefViewer viewer = new RecDefViewer(recDef);
        viewer.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        viewer.setVisible(true);
    }
}
