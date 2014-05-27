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
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.SwingHelper;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Pattern;

import static eu.delving.sip.base.SwingHelper.StringTransferable;
import static eu.delving.sip.base.SwingHelper.scrollVH;

/**
 * This frame shows an input record's XML in the form of a tree, with an added ability to copy the values from
 * any node for other purposes.  There are controls to create selection criteria for choosing particular
 * records as the scan proceeds, and buttons for going to the first or the next record.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class InputFrame extends FrameBase {
    private static final DefaultTreeModel EMPTY_MODEL = new DefaultTreeModel(new DefaultMutableTreeNode("No input"));
    private JTree recordTree;
    private JComboBox<Filter> filterBox = new JComboBox<Filter>(Filter.values());
    private JTextField filterField = new JTextField();

    private enum Filter {
        REGEX("Regex"),
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

    public InputFrame(SipModel sipModel) {
        super(Which.INPUT, sipModel, "Input");
        sipModel.addParseListener(new SipModel.ParseListener() {
            @Override
            public void updatedRecord(MetadataRecord metadataRecord) {
                exec(new RecordSetter(metadataRecord));
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
        p.add(filterBox, BorderLayout.WEST);
        p.add(filterField, BorderLayout.CENTER);
        p.add(createButtonPanel(), BorderLayout.SOUTH);
        return p;
    }

    private JPanel createButtonPanel() {
        JPanel p = new JPanel(new GridLayout(1, 0));
        p.add(new JButton(rewind));
        p.add(new JButton(play));
        return p;
    }

    private class RecordSetter implements Swing {
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
        private String attrKey;
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
                String truncated = node.text();
                if (truncated.contains("\n") || truncated.length() >= MAX_LENGTH) {
                    int index = truncated.indexOf('\n');
                    if (index > 0) truncated = truncated.substring(0, index);
                    if (truncated.length() >= MAX_LENGTH) truncated = truncated.substring(0, MAX_LENGTH);
                    string = String.format("<html><b>%s</b> = %s ...</html>", node.getNodeName(), truncated);
                    toolTip = tameTooltipText(node.text());
                }
                else {
                    string = String.format("<html><b>%s</b> = %s</html>", node.getNodeName(), truncated);
                    toolTip = truncated;
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

        private String tameTooltipText(String text) {
            List<String> lines = new ArrayList<String>();
            while (text.length() > MAX_LENGTH) {
                int pos = text.indexOf(' ', MAX_LENGTH - 10);
                if (pos < 0) break;
                if (pos > MAX_LENGTH + 5) pos = MAX_LENGTH;
                lines.add(text.substring(0, pos).trim());
                text = text.substring(pos).trim();
            }
            if (!text.trim().isEmpty()) lines.add(text);
            StringBuilder html = new StringBuilder("<html>");
            for (String line : lines) html.append(line).append("<br/>\n");
            return html.toString();
        }

        @Override
        public int compareTo(GroovyTreeNode gtn) {
            if (attrKey != null && gtn.attrKey == null) return -1;
            if (attrKey == null && gtn.attrKey != null) return 1;
            if (attrKey != null) return attrKey.compareTo(gtn.attrKey);
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
                    setIcon(SwingHelper.ICON_ATTRIBUTE);
                }
                else if (!node.isLeaf()) {
                    setIcon(SwingHelper.ICON_COMPOSITE);
                }
                else {
                    setIcon(SwingHelper.ICON_VALUE);
                }
            }
            else {
                setIcon(SwingHelper.ICON_COMPOSITE);
            }
            return component;
        }
    }

    private RewindAction rewind = new RewindAction();

    private class RewindAction extends AbstractAction {
        private RewindAction() {
            super("First");
            putValue(Action.SMALL_ICON, SwingHelper.ICON_REWIND);
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
            super("Next");
            putValue(Action.SMALL_ICON, SwingHelper.ICON_PLAY);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            setEnabled(false);
            sipModel.seekRecord(createPredicate(), new Swing() {
                @Override
                public void run() {
                    setEnabled(true);
                }
            });
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
}
