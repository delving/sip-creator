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
import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.base.Utility;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;
import java.util.regex.Pattern;

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
        filterField.addActionListener(rewind);
    }

    @Override
    protected void buildContent(Container content) {
        content.add(scroll(recordTree), BorderLayout.CENTER);
        content.add(createRecordButtonPanel(), BorderLayout.SOUTH);
    }

    private JPanel createRecordButtonPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Filter"));
        JPanel bp = new JPanel(new GridLayout(1,0));
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

    private class GroovyTreeNode implements TreeNode {
        private static final int MAX_LENGTH = 40;
        private MetadataRecord metadataRecord;
        private GroovyTreeNode parent;
        private GroovyNode node;
        private Vector<TreeNode> children = new Vector<TreeNode>();
        private String string;
        private String toolTip;
        
        private GroovyTreeNode(MetadataRecord metadataRecord) {
            this(null, metadataRecord.getRootNode());
            this.metadataRecord = metadataRecord;
        }

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
            return metadataRecord == null ? string : String.format("record %d", metadataRecord.getRecordNumber());
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

    private RewindAction rewind = new RewindAction();
    private class RewindAction extends AbstractAction {
        private RewindAction() {
            putValue(Action.SMALL_ICON, Utility.REWIND_ICON);
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
            putValue(Action.SMALL_ICON, Utility.PLAY_ICON);
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
        switch ((Filter)filterBox.getSelectedItem()) {
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
            default :
                throw new RuntimeException();
        }
    }
    
}
