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
import eu.delving.sip.base.StatsTreeNode;
import eu.delving.sip.model.BookmarksTreeModel;
import eu.delving.sip.model.RecDefTreeNode;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

/**
 * Putting the rec def into a JTree
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class RecDefViewer extends JFrame {
    private static final DataFlavor FLAVOR = new DataFlavor(StatsTreeNode.class, "node");
    private static final int MARGIN = 10;
    private static final Icon BOOKMARK_EXPANDED_ICON = new ImageIcon(Icon.class.getResource("/icons/bookmark-expanded-icon.png"));
    private static final Icon BOOKMARK_ICON = new ImageIcon(Icon.class.getResource("/icons/bookmark-icon.png"));
    private static final Icon VALUE_ELEMENT_ICON = new ImageIcon(Icon.class.getResource("/icons/value-element-icon.png"));
    private static final Icon COMPOSITE_ELEMENT_ICON = new ImageIcon(Icon.class.getResource("/icons/composite-element-icon.png"));
    private static final Icon ATTRIBUTE_ICON = new ImageIcon(Icon.class.getResource("/icons/attribute-icon.png"));
    private HtmlPanel recDefPanel = new HtmlPanel("Details");
    private HtmlPanel bookmarkPanel = new HtmlPanel("Details");
    private JTree recDefTree, bookmarksTree;

    public RecDefViewer(RecDef recDef) {
        super("RecDef Viewer");
        recDefTree = new JTree(new DefaultTreeModel(RecDefTreeNode.create(RecDefNode.create(recDef))));
        recDefTree.setCellRenderer(new RecDefTreeNode.Renderer());
        recDefTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        recDefTree.getSelectionModel().addTreeSelectionListener(new RecDefSelection());
        recDefTree.collapseRow(0);
        recDefTree.setDropMode(DropMode.ON);
        recDefTree.setTransferHandler(new Xfer());
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

    private class Xfer extends TransferHandler {

        @Override
        public Icon getVisualRepresentation(Transferable transferable) {
            StatsTreeNode node;
            try {
                node = (StatsTreeNode) transferable.getTransferData(FLAVOR);
                if (node.getTag().isAttribute()) {
                    return ATTRIBUTE_ICON;
                }
                else if (node.getChildNodes().iterator().hasNext()) {
                    return COMPOSITE_ELEMENT_ICON;
                }
                else {
                    return VALUE_ELEMENT_ICON;
                }
            }
            catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        public int getSourceActions(javax.swing.JComponent jComponent) {
            return LINK;
        }

        @Override
        public Transferable createTransferable(JComponent c) {
            return new Transferable() {
                @Override
                public DataFlavor[] getTransferDataFlavors() {
                    return new DataFlavor[]{FLAVOR};
                }

                @Override
                public boolean isDataFlavorSupported(DataFlavor dataFlavor) {
                    return dataFlavor.equals(FLAVOR);
                }

                @Override
                public Object getTransferData(DataFlavor dataFlavor) throws UnsupportedFlavorException, IOException {
//                    return analysisTree.getSelectionPath().getLastPathComponent();
                    return "Gumby!";
                }
            };
        }

        @Override
        public boolean canImport(TransferHandler.TransferSupport info) {
//             if (!info.isDataFlavorSupported(DataFlavor.stringFlavor))
//             JTree.DropLocation dl = (JTree.DropLocation)info.getDropLocation();
//             TreePath path = dl.getPath();
            return true;
        }

        @Override
        public boolean importData(TransferHandler.TransferSupport info) {
            if (!canImport(info)) return false;
            StatsTreeNode node;
            try {
                node = (StatsTreeNode) info.getTransferable().getTransferData(FLAVOR);
            }
            catch (Exception e) {
                e.printStackTrace();
                return false;
            }
            JTree.DropLocation location = (JTree.DropLocation) info.getDropLocation();
            TreePath path = location.getPath();
            RecDefTreeNode target = (RecDefTreeNode) path.getLastPathComponent();
            target.setStatsTreeNode(node);
            showNode(target.getRecDefNode());
            System.out.println("Dropped " + node + " into " + target);
            return true;
        }
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
                .setTemplate(node.isAttr() ? "templates/recdef-attribute" : "templates/recdef-element")
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
                        .setTemplate("templates/bookmark-category")
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
                        .setTemplate(ref.isAttr() ? "templates/bookmark-attribute" : "templates/bookmark-element")
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
                setIcon(expanded ? BOOKMARK_EXPANDED_ICON : BOOKMARK_ICON);
            }
            else if (value instanceof RecDef.Ref) {
                RecDef.Ref ref = (RecDef.Ref) value;
                if (ref.isAttr()) {
                    setIcon(ATTRIBUTE_ICON);
                }
                else if (!ref.elem.elemList.isEmpty()) {
                    setIcon(COMPOSITE_ELEMENT_ICON);
                }
                else {
                    setIcon(VALUE_ELEMENT_ICON);
                }
            }
            return component;
        }
    }

    public static void main(String[] args) throws IOException {
        RecDef recDef = RecDef.read(RecDefViewer.class.getResource("/lido-recdef.xml").openStream());
        recDef.resolve();
        RecDefViewer viewer = new RecDefViewer(recDef);
        viewer.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        viewer.setVisible(true);
    }
}
