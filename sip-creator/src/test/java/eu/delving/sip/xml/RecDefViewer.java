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

import eu.delving.metadata.*;
import eu.delving.sip.base.HtmlPanel;
import eu.delving.sip.files.Statistics;
import eu.delving.sip.model.BookmarksTreeModel;
import eu.delving.sip.model.RecDefNode;
import org.apache.commons.io.IOUtils;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.io.*;
import java.net.URL;
import java.util.ArrayList;

/**
 * Putting the rec def into a JTree
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class RecDefViewer extends JFrame {
    private static final DataFlavor FLAVOR = new DataFlavor(AnalysisTreeNode.class, "node");
    private static final int MARGIN = 10;
    private static final Icon BOOKMARK_EXPANDED_ICON = new ImageIcon(Icon.class.getResource("/bookmark-expanded-icon.png"));
    private static final Icon BOOKMARK_ICON = new ImageIcon(Icon.class.getResource("/bookmark-icon.png"));
    private static final Icon VALUE_ELEMENT_ICON = new ImageIcon(Icon.class.getResource("/value-element-icon.png"));
    private static final Icon COMPOSITE_ELEMENT_ICON = new ImageIcon(Icon.class.getResource("/composite-element-icon.png"));
    private static final Icon ATTRIBUTE_ICON = new ImageIcon(Icon.class.getResource("/attribute-icon.png"));
    private HtmlPanel recDefPanel = new HtmlPanel("Details");
    private HtmlPanel bookmarkPanel = new HtmlPanel("Details");
    private HistogramModel histogramModel = new HistogramModel();
    private RandomSampleModel randomSampleModel = new RandomSampleModel();
    private DefaultTreeModel analysisModel;
    private JTree analysisTree, recDefTree, bookmarksTree;

    public RecDefViewer(RecDef recDef) {
        super("RecDef Viewer");
        recDefTree = new JTree(new DefaultTreeModel(RecDefNode.create(recDef)));
        recDefTree.setCellRenderer(new RecDefRenderer());
        recDefTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        recDefTree.getSelectionModel().addTreeSelectionListener(new RecDefSelection());
        recDefTree.collapseRow(0);
        recDefTree.setDropMode(DropMode.ON);
        recDefTree.setTransferHandler(new Xfer());
        analysisTree = new JTree();
        analysisTree.setCellRenderer(new AnalysisTreeRenderer());
        analysisTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        analysisTree.getSelectionModel().addTreeSelectionListener(new AnalysisSelection());
        analysisTree.setDragEnabled(true);
        analysisTree.setTransferHandler(new Xfer());
        analysisModel = (DefaultTreeModel) analysisTree.getModel();
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
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(createMenu());
        setJMenuBar(menuBar);
        setSize(Toolkit.getDefaultToolkit().getScreenSize());
    }

    private JMenu createMenu() {
        JMenu menu = new JMenu("Choose Statistics");
        Action last = null;
        try {
            for (String line : IOUtils.readLines(getClass().getResource("/stats/ls.txt").openStream())) {
                menu.add(last = new StatsAction(line));
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        if (last != null) last.actionPerformed(null);
        return menu;
    }

    private void fill(Container content) {
        content.add(createCenter(), BorderLayout.CENTER);
    }

    private JPanel createCenter() {
        JPanel p = new JPanel(new GridLayout(1, 0));
        p.add(createAnalysisPanel());
        p.add(createRecDefPanel());
        return p;
    }

    private JPanel createAnalysisPanel() {
        JPanel p = new JPanel(new GridLayout(1, 0));
        p.setBorder(BorderFactory.createEmptyBorder(MARGIN, MARGIN, MARGIN, 0));
        p.add(createAnalysisTreePanel());
        p.add(createStatsPanel());
        return p;
    }

    private JPanel createStatsPanel() {
        JPanel p = new JPanel(new GridLayout(0, 1));
        p.add(createRandomSamplePanel());
        p.add(createHistogramPanel());
        return p;
    }

    private JPanel createAnalysisTreePanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Analysis"));
        p.add(new JScrollPane(analysisTree));
        return p;
    }

    private JComponent createRandomSamplePanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Random Sample"));
        JList list = new JList(randomSampleModel);
        p.add(new JScrollPane(list));
        return p;
    }

    private JComponent createHistogramPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Histogram"));
        JList list = new JList(histogramModel);
        p.add(new JScrollPane(list));
        return p;
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
            AnalysisTree.Node node;
            try {
                node = (AnalysisTree.Node) transferable.getTransferData(FLAVOR);
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
                    return analysisTree.getSelectionPath().getLastPathComponent();
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
            AnalysisTree.Node node;
            try {
                node = (AnalysisTree.Node) info.getTransferable().getTransferData(FLAVOR);
            }
            catch (Exception e) {
                e.printStackTrace();
                return false;
            }
            JTree.DropLocation location = (JTree.DropLocation) info.getDropLocation();
            TreePath path = location.getPath();
            RecDefNode target = (RecDefNode) path.getLastPathComponent();
            target.setNode(node);
            showNode(target);
            System.out.println("Dropped " + node + " into " + target);
            return true;
        }
    }

    private class StatsAction extends AbstractAction {
        private String fileName;

        StatsAction(String fileName) {
            super(fileName);
            this.fileName = fileName;
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            URL url = getClass().getResource("/stats/" + fileName);
            try {
                Statistics statistics = readStatistics(url.openStream());
                analysisModel.setRoot(statistics.createAnalysisTree().getRoot());
                analysisTree.collapseRow(0);
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private Statistics readStatistics(InputStream is) {
        try {
            ObjectInputStream in = new ObjectInputStream(is);
            @SuppressWarnings("unchecked")
            Statistics statistics = (Statistics) in.readObject();
            in.close();
            return statistics;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private class RecDefSelection implements TreeSelectionListener {

        @Override
        public void valueChanged(TreeSelectionEvent event) {
            RecDefNode node = (RecDefNode) event.getPath().getLastPathComponent();
            showNode(node);
            RecDefNode root = (RecDefNode) recDefTree.getModel().getRoot();
            root.showPath(recDefTree, node.getPath().getOurPath());
        }

    }

    private void showNode(RecDefNode node) {
        recDefPanel
                .setTemplate(node.isAttr() ? "templates/recdef-attribute" : "templates/recdef-element")
                .put("name", node.getTag())
                .put("doc", node.getDoc())
                .put("options", node.getOptions())
                .put("node", node.getNode())
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
            return getTreePath(path, (RecDefNode) model.getRoot());
        }

        private TreePath getTreePath(Path path, RecDefNode node) {
            if (node.getOurPath().equals(path)) {
                return node.getPath();
            }
            for (RecDefNode sub : node.getKids()) {
                TreePath subPath = getTreePath(path, sub);
                if (subPath != null) return subPath;
            }
            return null;
        }
    }

    private class AnalysisSelection implements TreeSelectionListener {

        @Override
        public void valueChanged(TreeSelectionEvent treeSelectionEvent) {
            AnalysisTree.Node node = (AnalysisTree.Node) treeSelectionEvent.getPath().getLastPathComponent();
            if (node.getStatistics() == null) {
                histogramModel.setHistogram(null);
                randomSampleModel.setRandomSample(null);
            }
            else {
                histogramModel.setHistogram(node.getStatistics().getHistogram());
                randomSampleModel.setRandomSample(node.getStatistics().getRandomSample());
            }
            AnalysisTree.Node root = (AnalysisTree.Node) analysisModel.getRoot();
            root.showPath(analysisTree, node.getPath());
        }
    }

    private class AnalysisTreeRenderer extends DefaultTreeCellRenderer {

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            Component component = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            if (value instanceof AnalysisTree.Node) {
                AnalysisTree.Node node = (AnalysisTree.Node) value;
                if (node.getTag().isAttribute()) {
                    setIcon(ATTRIBUTE_ICON);
                }
                else if (node.getChildNodes().iterator().hasNext()) {
                    setIcon(COMPOSITE_ELEMENT_ICON);
                }
                else {
                    setIcon(VALUE_ELEMENT_ICON);
                }
            }
            return component;
        }
    }

    private class RecDefRenderer extends DefaultTreeCellRenderer {

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            Component component = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            RecDefNode node = (RecDefNode) value;
            if (node.isAttr()) {
                setIcon(ATTRIBUTE_ICON);
            }
            else if (node.hasChildElements()) {
                setIcon(COMPOSITE_ELEMENT_ICON);
            }
            else {
                setIcon(VALUE_ELEMENT_ICON);
            }
            return component;
        }
    }

    private class BookmarkRenderer extends DefaultTreeCellRenderer {

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

    private static class HistogramModel extends AbstractListModel {

        private java.util.List<Histogram.Counter> list = new ArrayList<Histogram.Counter>();

        public void setHistogram(Histogram histogram) {
            int size = getSize();
            list.clear();
            fireIntervalRemoved(this, 0, size);
            if (histogram != null) {
                list.addAll(histogram.getTrimmedCounters());
                fireIntervalAdded(this, 0, getSize());
            }
        }

        @Override
        public int getSize() {
            return list.size();
        }

        @Override
        public Object getElementAt(int i) {
            Histogram.Counter counter = list.get(i);
            return String.format("   %d (%s) : '%s'", counter.getCount(), counter.getPercentage(), counter.getValue());
        }
    }

    private static class RandomSampleModel extends AbstractListModel {

        private java.util.List<String> list = new ArrayList<String>();

        public void setRandomSample(RandomSample randomSample) {
            int size = getSize();
            list.clear();
            fireIntervalRemoved(this, 0, size);
            if (randomSample != null) {
                list.addAll(randomSample.getValues());
                fireIntervalAdded(this, 0, getSize());
            }
        }

        @Override
        public int getSize() {
            return list.size();
        }

        @Override
        public Object getElementAt(int i) {
            return "   " + list.get(i);
        }
    }


    private static final File FILE = new File("sip-creator/src/test/resources/lido-recdef.xml");

    public static void main(String[] args) throws FileNotFoundException {
        RecDef recDef = RecDef.read(new FileInputStream(FILE));
        recDef.resolve();
        RecDefViewer viewer = new RecDefViewer(recDef);
        viewer.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        viewer.setVisible(true);
    }
}
