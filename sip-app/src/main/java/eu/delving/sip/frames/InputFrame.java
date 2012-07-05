/*
 * Copyright 2011, 2012 Delving BV
 *
 * Licensed under the EUPL, Version 1.0 or as soon they
 * will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 * you may not use this work except in compliance with the
 * Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl
 *
 * Unless required by applicable law or agreed to in
 * writing, software distributed under the Licence is
 * distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

package eu.delving.sip.frames;

import eu.delving.groovy.GroovyNode;
import eu.delving.groovy.MetadataRecord;
import eu.delving.sip.base.Exec;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.base.SwingHelper;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

import static eu.delving.sip.base.SwingHelper.scrollVH;

/**
 * The transformation from input record to output
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class InputFrame extends FrameBase {
    private static final DefaultTreeModel EMPTY_MODEL = new DefaultTreeModel(new DefaultMutableTreeNode("No input"));
    private JTree recordTree;
    private JComboBox filterBox = new JComboBox(Filter.values());
    private JTextField filterField = new JTextField();

    private enum Filter {
        REGEX("Regex"),
        REGEX_CI("Regex (CI)"),
        MODULO("Modulo");

        private String name;

        private Filter(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public InputFrame(JDesktopPane desktop, SipModel sipModel) {
        super(Which.INPUT, desktop, sipModel, "Input");
        sipModel.addParseListener(new SipModel.ParseListener() {
            @Override
            public void updatedRecord(MetadataRecord metadataRecord) {
                if (metadataRecord == null) {
                    Exec.swing(new RecordSetter(null));
                }
                else {
                    Exec.swing(new RecordSetter(metadataRecord));
                }
            }
        });
        recordTree = new JTree(EMPTY_MODEL) {
            @Override
            public String getToolTipText(MouseEvent evt) {
                TreePath treePath = recordTree.getPathForLocation(evt.getX(), evt.getY());
                return treePath != null ? ((GroovyTreeNode) treePath.getLastPathComponent()).toolTip : "";
            }
        };
        recordTree.setToolTipText("Input Record");
        recordTree.setCellRenderer(new Renderer());
        recordTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        recordTree.setTransferHandler(new TreeTransferHandler());
        filterField.addActionListener(rewind);
    }

    @Override
    protected void buildContent(Container content) {
        content.add(scrollVH(recordTree), BorderLayout.CENTER);
        content.add(createRecordButtonPanel(), BorderLayout.SOUTH);
    }

    private JPanel createRecordButtonPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Filter"));
        JPanel bp = new JPanel(new GridLayout(1, 0));
        bp.add(new JButton(rewind));
        bp.add(new JButton(play));
        p.add(bp, BorderLayout.EAST);
        p.add(filterBox, BorderLayout.WEST);
        p.add(filterField, BorderLayout.CENTER);
        return p;
    }

    private class RecordSetter implements Runnable {
        private MetadataRecord metadataRecord;

        private RecordSetter(MetadataRecord metadataRecord) {
            this.metadataRecord = metadataRecord;
        }

        @Override
        public void run() {
            if (metadataRecord == null) {
                recordTree.setModel(EMPTY_MODEL);
            }
            else {
                GroovyTreeNode root = new GroovyTreeNode(metadataRecord);
                recordTree.setModel(new DefaultTreeModel(root));
                root.expand();
            }
        }
    }

    private class GroovyTreeNode implements TreeNode, Comparable<GroovyTreeNode> {
        private static final int MAX_LENGTH = 40;
        private MetadataRecord metadataRecord;
        private GroovyTreeNode parent;
        private String attrKey, attrValue;
        private GroovyNode node;
        private Vector<GroovyTreeNode> children = new Vector<GroovyTreeNode>();
        private String string;
        private String toolTip;

        private GroovyTreeNode(MetadataRecord metadataRecord) {
            this(null, metadataRecord.getRootNode());
            this.metadataRecord = metadataRecord;
        }

        private GroovyTreeNode(GroovyTreeNode parent, String attrKey, String attrValue) {
            this.parent = parent;
            this.attrKey = attrKey;
            this.attrValue = attrValue;
            string = String.format("<html><b>%s</b> = %s</html>", attrKey, attrValue);
            toolTip = string; // todo
        }

        private GroovyTreeNode(GroovyTreeNode parent, GroovyNode node) {
            this.parent = parent;
            this.node = node;
            for (Map.Entry<String, String> entry : node.attributes().entrySet()) {
                children.add(new GroovyTreeNode(this, entry.getKey(), entry.getValue()));
            }
            if (node.getNodeValue() instanceof List) {
                string = node.getNodeName();
                toolTip = String.format("Size: %d", ((List) node.getNodeValue()).size());
            }
            else {
                String value = node.text();
                if (value.contains("\n") || value.length() >= MAX_LENGTH) {
                    int index = value.indexOf('\n');
                    if (index > 0) {
                        value = value.substring(0, index);
                    }
                    if (value.length() >= MAX_LENGTH) {
                        value = value.substring(0, MAX_LENGTH);
                    }
                    string = String.format("<html><b>%s</b> = %s ...</html>", node.getNodeName(), value);
                    toolTip = node.text();
                }
                else {
                    string = String.format("<html><b>%s</b> = %s</html>", node.getNodeName(), value);
                    toolTip = value;
                }
            }
            if (this.node.getNodeValue() instanceof List) {
                for (Object sub : ((List) this.node.getNodeValue())) {
                    GroovyNode subnode = (GroovyNode) sub;
                    children.add(new GroovyTreeNode(this, subnode));
                }
                Collections.sort(children);
            }
        }

        @Override
        public TreeNode getChildAt(int i) {
            return children.elementAt(i);
        }

        @Override
        public int getChildCount() {
            return children.size();
        }

        @Override
        public TreeNode getParent() {
            return parent;
        }

        @Override
        public int getIndex(TreeNode treeNode) {
            return children.indexOf(treeNode);
        }

        @Override
        public boolean getAllowsChildren() {
            return !children.isEmpty();
        }

        @Override
        public boolean isLeaf() {
            return children.isEmpty();
        }

        @Override
        public Enumeration children() {
            return children.elements();
        }

        @Override
        public String toString() {
            return metadataRecord != null ? String.format("record %d", metadataRecord.getRecordNumber()) : string;
        }

        public void expand() {
            if (!isLeaf()) {
                Timer timer = new Timer(50, new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        recordTree.expandPath(getTreePath());
                        for (TreeNode sub : children) {
                            ((GroovyTreeNode) sub).expand();
                        }
                    }
                });
                timer.setRepeats(false);
                timer.start();
            }
        }

        public TreePath getTreePath() {
            List<TreeNode> list = new ArrayList<TreeNode>();
            compilePathList(list);
            return new TreePath(list.toArray());
        }

        private void compilePathList(List<TreeNode> list) {
            if (parent != null) parent.compilePathList(list);
            list.add(this);
        }

        @Override
        public int compareTo(GroovyTreeNode gtn) {
            if (attrKey != null && gtn.attrKey == null) return -1;
            if (attrKey == null && gtn.attrKey != null) return 1;
            if (attrKey != null && gtn.attrKey != null) return attrKey.compareTo(gtn.attrKey);
            return node.getNodeName().compareTo(gtn.node.getNodeName());
        }
    }

    public static class Renderer extends DefaultTreeCellRenderer {

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            Component component = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            if (value instanceof GroovyTreeNode) {
                GroovyTreeNode node = (GroovyTreeNode) value;
                if (node.attrKey != null) {
                    setIcon(SwingHelper.ATTRIBUTE_ICON);
                }
                else if (!node.isLeaf()) {
                    setIcon(SwingHelper.COMPOSITE_ELEMENT_ICON);
                }
                else {
                    setIcon(SwingHelper.VALUE_ELEMENT_ICON);
                }
            }
            else {
                setIcon(SwingHelper.COMPOSITE_ELEMENT_ICON);
            }
            return component;
        }
    }

    private RewindAction rewind = new RewindAction();

    private class RewindAction extends AbstractAction {
        private RewindAction() {
            putValue(Action.SMALL_ICON, SwingHelper.REWIND_ICON);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            sipModel.seekReset();
            play.actionPerformed(actionEvent);
        }
    }

    private PlayAction play = new PlayAction();

    private class PlayAction extends AbstractAction {
        private PlayAction() {
            putValue(Action.SMALL_ICON, SwingHelper.PLAY_ICON);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            ProgressListener progress = sipModel.getFeedback().progressListener("Scanning");
            progress.setProgressMessage(String.format("Scanning for %s: %s", filterBox.getSelectedItem(), filterField.getText().trim()));
            sipModel.seekRecord(createPredicate(), progress);
        }
    }

    private SipModel.ScanPredicate createPredicate() {
        final String filterString = filterField.getText().trim();
        switch ((Filter) filterBox.getSelectedItem()) {
            case REGEX:
                return new SipModel.ScanPredicate() {
                    @Override
                    public boolean accept(MetadataRecord record) {
                        return record.contains(Pattern.compile(filterString));
                    }
                };
            case REGEX_CI:
                return new SipModel.ScanPredicate() {
                    @Override
                    public boolean accept(MetadataRecord record) {
                        return record.contains(Pattern.compile(filterString, Pattern.CASE_INSENSITIVE));
                    }
                };
            case MODULO:
                int modulo;
                try {
                    modulo = Integer.parseInt(filterString);
                }
                catch (NumberFormatException e) {
                    modulo = 1;
                }
                if (modulo <= 0) modulo = 1;
                final int recordNumberModulo = modulo;
                return new SipModel.ScanPredicate() {
                    @Override
                    public boolean accept(MetadataRecord record) {
                        return recordNumberModulo == 1 || record.getRecordNumber() % recordNumberModulo == 0;
                    }
                };
            default:
                throw new RuntimeException();
        }
    }

    private static class TreeTransferHandler extends TransferHandler {

        protected Transferable createTransferable(JComponent c) {
            JTree tree = (JTree) c;
            TreePath[] paths = tree.getSelectionPaths();
            if (paths == null || paths.length != 1) return null;
            TreePath path = tree.getSelectionPath();
            GroovyTreeNode groovyTreeNode = (GroovyTreeNode) path.getLastPathComponent();
            return new StringTransferable((String) (groovyTreeNode.node.getNodeValue()));
        }

        public int getSourceActions(JComponent c) {
            return COPY;
        }
    }

    private static class StringTransferable implements Transferable {
        private String string;

        private static final DataFlavor[] flavors = {DataFlavor.stringFlavor};

        private StringTransferable(String string) {
            this.string = string;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return flavors;
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return flavor.equals(flavors[0]);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
            return string;
        }
    }
}
