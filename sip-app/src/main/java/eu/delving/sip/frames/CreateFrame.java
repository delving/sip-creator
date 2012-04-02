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

import eu.delving.metadata.NodeMapping;
import eu.delving.sip.base.Exec;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.model.CreateModel;
import eu.delving.sip.model.RecDefTreeNode;
import eu.delving.sip.model.SipModel;
import eu.delving.sip.model.SourceTreeNode;
import eu.delving.sip.panels.HtmlPanel;
import eu.delving.sip.panels.NodeMappingPanel;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.SortedSet;

/**
 * Choices have been made in the trees, now we actually make the mapping
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class CreateFrame extends FrameBase {
    private static final String SELECT_STATS = "<html><center><h1>Select</h1></center></html>";
    private static final String SELECT_RECDEF = "<html><center><h1>Select</h1></center></html>";
    private CreateModel createModel;
    private HtmlPanel statsHtml = new HtmlPanel("Source Statistics");
    private HtmlPanel recDefHtml = new HtmlPanel("Target Documentation");
    private CreateMappingAction createMappingAction;

    public CreateFrame(JDesktopPane desktop, SipModel sipModel) {
        super(Which.CREATE, desktop, sipModel, "Create", false);
        createModel = sipModel.getCreateModel();
        createMappingAction = new CreateMappingAction();
        statsHtml.setHtml(SELECT_STATS);
        recDefHtml.setHtml(SELECT_RECDEF);
        createModel.addListener(new CreateModel.Listener() {
            @Override
            public void statsTreeNodeSet(CreateModel createModel) {
                SortedSet<SourceTreeNode> sourceTreeNodes = createModel.getSourceTreeNodes();
                if (sourceTreeNodes == null) {
                    statsHtml.setHtml(SELECT_STATS);
                }
                else {
                    StringBuilder out = new StringBuilder("<html><table>");
                    for (SourceTreeNode node : sourceTreeNodes) {
                        out.append("<tr><td>");
                        out.append(node.toHtmlChunk());
                        out.append("</td></tr>");
                    }
                    out.append("</table></html>");
                    statsHtml.setHtml(out.toString());
                }
                createMappingAction.refresh();
            }

            @Override
            public void recDefTreeNodeSet(CreateModel createModel) {
                RecDefTreeNode recDefTreeNode = createModel.getRecDefTreeNode();
                recDefHtml.setHtml(recDefTreeNode != null ? recDefTreeNode.toHtml() : SELECT_RECDEF);
                createMappingAction.refresh();
            }

            @Override
            public void nodeMappingSet(CreateModel createModel) {
                createMappingAction.refresh();
            }

            @Override
            public void nodeMappingChanged(CreateModel createModel) {
                // todo: maybe do some update, either here or in MappingCompileModel
            }
        });
    }

    public CreateMappingAction getAction() {
        return createMappingAction;
    }

    @Override
    protected void buildContent(Container content) {
        content.setLayout(new BorderLayout(8, 8));
        content.add(createPanel(), BorderLayout.CENTER);
        content.add(new JButton(createMappingAction), BorderLayout.SOUTH);
    }

    private JComponent createPanel() {
        JPanel p = new JPanel(new GridLayout(0, 1, 5, 5));
        p.add(statsHtml);
        p.add(recDefHtml);
        return p;
    }
    
    private class MappingHintFrame extends FrameBase implements ActionListener {
        private NodeMappingPanel nodeMappingPanel = new NodeMappingPanel();
        private JButton apply = new JButton("Apply selected mappings");

        private MappingHintFrame() {
            super(Which.MAPPING_HINTS, CreateFrame.this, CreateFrame.this.sipModel, "Mapping Hints", true);
            setPlacement(CreateFrame.this.getPlacement());
            nodeMappingPanel.setList(sipModel.getStatsModel().getCandidateMappings());
            nodeMappingPanel.getNodeMappingList().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            apply.addActionListener(this);
        }

        @Override
        protected void buildContent(Container content) {
            content.add(nodeMappingPanel, BorderLayout.CENTER);
            content.add(apply, BorderLayout.SOUTH);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            Object[] selectedValues = nodeMappingPanel.getNodeMappingList().getSelectedValues();
            for (Object valueObject : selectedValues) {
                NodeMapping nodeMapping = (NodeMapping) valueObject;
                System.out.println("SELECTED: "+nodeMapping);
            }
            closeFrame();
        }
    }

    private class CreateMappingAction extends AbstractAction {
        private static final String CREATE = "<html><h3>Create mapping</h3></html>";
        private static final String EXISTS = "<html><h3>Mapping has been created</h3></html>";
        private static final String SELECT = "<html><h3>Select source and target</h3></html>";
        private static final String HINT = "<html><h3>Select a mapping hint</h3></html>";

        private CreateMappingAction() {
            super(createModel.canCreate() ? SELECT : HINT);
            setEnabled(!createModel.canCreate());
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            if (createModel.canCreate()) {
                Exec.work(new Runnable() {
                    @Override
                    public void run() {
                        createModel.createMapping();
                    }
                });
            }
            else {
                MappingHintFrame mappingHintFrame = new MappingHintFrame();
                mappingHintFrame.openFrame();
            }
        }

        public void refresh() {
            if (createModel.canCreate()) {
                setEnabled(true);
                StringBuilder tooltip = new StringBuilder("<html><table cellpadding=10><tr><td><h3>From:</h3><ul>");
                for (SourceTreeNode node : createModel.getSourceTreeNodes()) {
                    tooltip.append("<li>").append(node.getPath(false).toString()).append("</li>");
                }
                tooltip.append("</ul><h3>To:</h3><ul>");
                tooltip.append("<li>").append(createModel.getRecDefTreeNode().getRecDefPath().getTagPath().toString()).append("</li>");
                tooltip.append("</ul></td></tr></table></html>");
                putValue(Action.NAME, CREATE);
                putValue(Action.SHORT_DESCRIPTION, tooltip.toString());
            }
            else if (createModel.getNodeMapping() != null) {
                setEnabled(false);
                putValue(Action.NAME, EXISTS);
                putValue(Action.SHORT_DESCRIPTION, "<html>The mapping has already been created.");
            }
            else if (!sipModel.getStatsModel().getCandidateMappings().isEmpty()) {
                setEnabled(true);
                putValue(Action.NAME, HINT);
                putValue(Action.SHORT_DESCRIPTION, "<html>Choose values from the input and output.");
            }
            else {
                setEnabled(false);
                putValue(Action.NAME, SELECT);
                putValue(Action.SHORT_DESCRIPTION, "<html>Choose values from the input and output.");
            }
        }
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(600, 440);
    }
}
