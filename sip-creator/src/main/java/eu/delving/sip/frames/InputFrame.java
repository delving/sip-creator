/*
 * Copyright 2011 DELVING BV
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
import eu.delving.sip.base.Utility;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;

/**
 * The transformation from input record to output
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class InputFrame extends FrameBase {
    private static final DefaultTreeModel EMPTY_MODEL = new DefaultTreeModel(new DefaultMutableTreeNode("No input"));
    private JTree recordTree;
    private JButton firstButton = new JButton("First");
    private JButton nextButton = new JButton("Next");
    private RecordScanPopup recordScanPopup;
    private JLabel criterionLabel = new JLabel();

    public InputFrame(JDesktopPane desktop, SipModel sipModel) {
        super(desktop, sipModel, "Input", false);
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
        this.recordScanPopup = new RecordScanPopup(this, sipModel, new RecordScanPopup.Listener() {
            @Override
            public void searchStarted(String description) {
                criterionLabel.setText(description);
            }
        });
        firstButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                recordScanPopup.scan(false);
            }
        });
        nextButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                recordScanPopup.scan(true);
            }
        });
    }

    @Override
    protected void buildContent(Container content) {
        content.add(scroll(recordTree), BorderLayout.CENTER);
        content.add(createRecordButtonPanel(), BorderLayout.SOUTH);
    }

    private JPanel createRecordButtonPanel() {
        int margin = 4;
        criterionLabel.setHorizontalAlignment(JLabel.CENTER);
        criterionLabel.setBackground(Color.WHITE);
        criterionLabel.setOpaque(true);
        criterionLabel.setBorder(BorderFactory.createEtchedBorder());
        JPanel p = new JPanel(new GridLayout(2, 2, margin, margin));
        p.setBorder(BorderFactory.createEmptyBorder(margin, margin, margin, margin));
        p.add(firstButton);
        p.add(nextButton);
        p.add(new JButton(recordScanPopup.getAction()));
        recordScanPopup.init();
        criterionLabel.setText(recordScanPopup.getPredicateDescription());
        p.add(criterionLabel);
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
                GroovyTreeNode root = new GroovyTreeNode(null, metadataRecord.getRootNode());
                recordTree.setModel(new DefaultTreeModel(root));
                root.expand();
            }
        }
    }

    private class GroovyTreeNode implements TreeNode {
        private static final int MAX_LENGTH = 40;
        private GroovyTreeNode parent;
        private GroovyNode node;
        private Vector<TreeNode> children = new Vector<TreeNode>();
        private String string;
        private String toolTip;

        private GroovyTreeNode(GroovyTreeNode parent, GroovyNode node) {
            this.parent = parent;
            this.node = node;
            if (node.value() instanceof List) {
                string = node.name();
                toolTip = String.format("Size: %d", ((List) node.value()).size());
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
                    string = String.format("<html><b>%s</b> = %s ...</html>", node.name(), value);
                    toolTip = node.text();
                }
                else {
                    string = String.format("<html><b>%s</b> = %s</html>", node.name(), value);
                    toolTip = value;
                }
            }
            if (this.node.value() instanceof List) {
                for (Object sub : ((List) this.node.value())) {
                    GroovyNode subnode = (GroovyNode) sub;
                    children.add(new GroovyTreeNode(this, subnode));
                }
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
            return string;
        }

        public void expand() {
            if (!isLeaf()) {
                Timer timer = new Timer(50, new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        recordTree.expandPath(getTreePath());
                        for (TreeNode sub : children) {
                            ((GroovyTreeNode)sub).expand();
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
    }

    public static class Renderer extends DefaultTreeCellRenderer {

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            Component component = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            if (value instanceof GroovyTreeNode) {
                GroovyTreeNode node = (GroovyTreeNode) value;
//                if (node) {
//                    setIcon(Utility.ATTRIBUTE_ICON);
//                }
                if (!node.isLeaf()) {
                    setIcon(Utility.COMPOSITE_ELEMENT_ICON);
                }
                else {
                    setIcon(Utility.VALUE_ELEMENT_ICON);
                }
            }
            else {
                setIcon(Utility.COMPOSITE_ELEMENT_ICON);
            }
            return component;
        }
    }

}
