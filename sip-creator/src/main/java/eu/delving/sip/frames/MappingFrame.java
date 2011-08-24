/*
 * Copyright 2010 DELVING BV
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

import eu.delving.metadata.AnalysisTree;
import eu.delving.metadata.AnalysisTreeNode;
import eu.delving.metadata.CodeGenerator;
import eu.delving.metadata.FieldMapping;
import eu.delving.metadata.FieldStatistics;
import eu.delving.metadata.Path;
import eu.delving.metadata.SourceVariable;
import eu.delving.sip.FileStore;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.model.SipModel;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDesktopPane;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.ArrayList;
import java.util.List;

/**
 * The structure of the input data, tree, variables and statistics.
 * From here mappings are made.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class MappingFrame extends FrameBase {
    private static final String RUN_ANALYSIS = "Run the Analysis";
    private static final String ELEMENTS_PROCESSED = "%d Elements Processed";
    private JButton selectRecordRootButton = new JButton("Select Record Root ");
    private JButton selectUniqueElementButton = new JButton("Select Unique Element");
    private JButton analyzeButton = new JButton(RUN_ANALYSIS);
    private JButton createObviousMappingButton = new JButton("Create obvious mappings");
    private JTextField constantField = new JTextField("?");
    private FieldStatisticsPanel fieldStatisticsPanel = new FieldStatisticsPanel();
    private JTree statisticsJTree;
    private JList variablesList;
    private TargetPopup targetPopup;
    private ObviousMappingsPopup obviousMappingsPopup;

    public MappingFrame(JDesktopPane desktop, SipModel sipModel) {
        super(desktop, sipModel, "Mapping", false);
        this.targetPopup = new TargetPopup(this, new TargetPopup.Context() {

            @Override
            public List<SourceVariable> createSelectedVariableList() {
                List<SourceVariable> list = new ArrayList<SourceVariable>();
                for (Object sourceVariable : variablesList.getSelectedValues()) {
                    list.add((SourceVariable) sourceVariable);
                }
                return list;
            }

            @Override
            public String getConstantFieldValue() {
                return constantField.getText();
            }

            @Override
            public void clear() {
                variablesList.clearSelection();
                constantField.setText("");
            }
        });
        obviousMappingsPopup = new ObviousMappingsPopup(this);
        variablesList = new JList(sipModel.getVariablesListWithCountsModel());
        statisticsJTree = new JTree(sipModel.getAnalysisTreeModel());
        statisticsJTree.getModel().addTreeModelListener(new Expander());
        statisticsJTree.setCellRenderer(new AnalysisTreeCellRenderer());
        statisticsJTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        wireUp();
    }

    @Override
    protected void buildContent(Container content) {
        content.setLayout(new GridLayout(1, 0, 5, 5));
        content.add(createLeft());
        content.add(createRight());
    }

    @Override
    protected void refresh() {
    }

    private void wireUp() {
        statisticsJTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent event) {
                TreePath path = event.getPath();
                if (statisticsJTree.getSelectionModel().isPathSelected(path)) {
                    final AnalysisTree.Node node = (AnalysisTree.Node) path.getLastPathComponent();
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            selectRecordRootButton.setEnabled(node.couldBeRecordRoot());
                            selectUniqueElementButton.setEnabled(!node.couldBeRecordRoot());
                            sipModel.setStatistics(node.getStatistics());
                        }
                    });
                }
                else {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            selectRecordRootButton.setEnabled(false);
                            selectUniqueElementButton.setEnabled(false);
                            sipModel.setStatistics(null);
                        }
                    });
                }
            }
        });
        sipModel.addUpdateListener(new SipModel.UpdateListener() {
            @Override
            public void updatedDataSetStore(FileStore.DataSetStore dataSetStore) {
                variablesList.clearSelection();
            }

            @Override
            public void updatedStatistics(final FieldStatistics fieldStatistics) {
                fieldStatisticsPanel.setStatistics(fieldStatistics);
            }

            @Override
            public void updatedRecordRoot(Path recordRoot, int recordCount) {
            }

            @Override
            public void normalizationMessage(boolean complete, String message) {
            }
        });
        createObviousMappingButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
//                obviousMappingDialog.setVisible(true);
            }
        });
        constantField.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                variablesList.clearSelection();
            }

            @Override
            public void focusLost(FocusEvent e) {
            }
        });
        variablesList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting()) return;
                final SourceVariable sourceVariable = (SourceVariable) variablesList.getSelectedValue();
                if (sourceVariable != null && sourceVariable.hasStatistics()) {
                    sipModel.setStatistics(sourceVariable.getStatistics());
                    constantField.setText("?");
                }
            }
        });
        selectRecordRootButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                TreePath path = statisticsJTree.getSelectionPath();
                AnalysisTreeNode node = (AnalysisTreeNode) path.getLastPathComponent();
                Path recordRoot = node.getPath();
                if (recordRoot != null) {
                    sipModel.setRecordRoot(recordRoot, node.getStatistics().getTotal());
                }
            }
        });
        selectUniqueElementButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                TreePath path = statisticsJTree.getSelectionPath();
                AnalysisTreeNode node = (AnalysisTreeNode) path.getLastPathComponent();
                sipModel.setUniqueElement(node.getPath());
            }
        });
        analyzeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                analyzeButton.setEnabled(false);
                performAnalysis();
            }
        });
    }

    private JComponent createLeft() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.add("Document Structure", createTreePanel());
        tabs.add("Variable Mapping", createVariableMappingPanel());
        return tabs;
    }

    private JPanel createVariableMappingPanel() {
        JPanel pp = new JPanel(new GridLayout(0, 1, 5, 5));
        pp.add(new JButton(obviousMappingsPopup.getAction()));
        pp.add(new JButton(targetPopup.getAction()));
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.add(createVariableMappingTop(), BorderLayout.CENTER);
        p.add(pp, BorderLayout.SOUTH);
        return p;
    }

    private JPanel createVariableMappingTop() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.setBorder(BorderFactory.createEtchedBorder());
        p.add(createVariableListPanel(), BorderLayout.CENTER);
        p.add(createConstantFieldPanel(), BorderLayout.SOUTH);
        return p;
    }

    private JPanel createVariableListPanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.setBorder(BorderFactory.createTitledBorder("Input Field Source"));
        p.add(scroll(variablesList), BorderLayout.CENTER);
        return p;
    }

    private JPanel createConstantFieldPanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.setBorder(BorderFactory.createTitledBorder("Constant Source"));
        p.add(constantField);
        return p;
    }

    private JPanel createTreePanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.setBorder(BorderFactory.createTitledBorder("Document Structure"));
        p.add(scroll(statisticsJTree), BorderLayout.CENTER);
        JPanel south = new JPanel(new GridLayout(0, 1));
        south.add(analyzeButton);
        south.add(createSelectButtonPanel());
        p.add(south, BorderLayout.SOUTH);
        return p;
    }

    private JPanel createSelectButtonPanel() {
        JPanel bp = new JPanel(new GridLayout(1, 0, 5, 5));
        selectRecordRootButton.setEnabled(false);
        bp.add(selectRecordRootButton);
        selectUniqueElementButton.setEnabled(false);
        bp.add(selectUniqueElementButton);
        return bp;
    }

    private JComponent createRight() {
        return fieldStatisticsPanel;
    }

    private void prepareCreateMappingButtons() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                CodeGenerator codeGenerator = new CodeGenerator();
                List<FieldMapping> obvious = codeGenerator.createObviousMappings(sipModel.getUnmappedFields(), sipModel.getVariables());
                if (obvious.isEmpty()) {
                    if (obviousMappingsPopup.isVisible()) {
                        obviousMappingsPopup.closeFrame();
                    }
                    createObviousMappingButton.setEnabled(false);
                }
                else {
                    createObviousMappingButton.setEnabled(true);
                }
            }
        });
    }

    private void performAnalysis() {
        sipModel.analyzeFields(new SipModel.AnalysisListener() {

            @Override
            public void finished(boolean success) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        setElementsProcessed(sipModel.getElementCount());
                        analyzeButton.setEnabled(true);
                    }
                });
            }

            @Override
            public void analysisProgress(final long elementCount) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        setElementsProcessed(elementCount);
                    }
                });
            }
        });
    }

    private void setElementsProcessed(long count) {
        analyzeButton.setText(String.format(ELEMENTS_PROCESSED, count));
    }

    private class AnalysisTreeCellRenderer extends DefaultTreeCellRenderer {
        private Font normalFont, thickFont;

        @Override
        public Component getTreeCellRendererComponent(JTree jTree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            JLabel label = (JLabel) super.getTreeCellRendererComponent(jTree, value, selected, expanded, leaf, row, hasFocus);
            AnalysisTree.Node node = (AnalysisTree.Node) value;
            label.setFont(getNormalFont());
            if (node.isRecordRoot()) {
                label.setFont(getThickFont());
                label.setText(String.format("%s : Record Root", node));
            }
            if (node.isUniqueElement()) {
                label.setFont(getThickFont());
                label.setText(String.format("%s : Unique Element", node));
            }
            return label;
        }

        private Font getNormalFont() {
            if (normalFont == null) {
                normalFont = super.getFont();
            }
            return normalFont;
        }

        private Font getThickFont() {
            if (thickFont == null) {
                thickFont = new Font(getNormalFont().getFontName(), Font.BOLD, getNormalFont().getSize());
            }
            return thickFont;
        }
    }

    private class Expander implements TreeModelListener {

        @Override
        public void treeNodesChanged(TreeModelEvent e) {
        }

        @Override
        public void treeNodesInserted(TreeModelEvent e) {
        }

        @Override
        public void treeNodesRemoved(TreeModelEvent e) {
        }

        @Override
        public void treeStructureChanged(TreeModelEvent e) {
            Timer timer = new Timer(500, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    expandEmptyNodes((AnalysisTree.Node) statisticsJTree.getModel().getRoot());
                }
            });
            timer.setRepeats(false);
            timer.start();
        }

        private void expandEmptyNodes(AnalysisTree.Node node) {
            if (node.couldBeRecordRoot()) {
                TreePath path = node.getTreePath();
                statisticsJTree.expandPath(path);
            }
            for (AnalysisTree.Node childNode : node.getChildNodes()) {
                expandEmptyNodes(childNode);
            }
        }
    }

    // todo: popup for obvious mappings
    // todo: popup for making a mapping
}
