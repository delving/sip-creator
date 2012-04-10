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

import eu.delving.sip.base.Exec;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.Utility;
import eu.delving.sip.model.*;
import eu.delving.sip.panels.HtmlPanel;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
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
    private JList mappingHintsList;
    private CreateMappingAction createMappingAction;
    private CopyMappingAction copyMappingAction;

    public CreateFrame(JDesktopPane desktop, SipModel sipModel) {
        super(Which.CREATE, desktop, sipModel, "Create", false);
        createModel = sipModel.getCreateModel();
        mappingHintsList = sipModel.getMappingHintsModel().getNodeMappingListModel().createJList();
        createMappingAction = new CreateMappingAction();
        copyMappingAction = new CopyMappingAction();
        statsHtml.setHtml(SELECT_STATS);
        recDefHtml.setHtml(SELECT_RECDEF);
        createModel.addListener(new CreateModel.Listener() {
            @Override
            public void sourceTreeNodesSet(CreateModel createModel) {
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
        final JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Select", createSelectPanel());
        tabs.addTab("Hints", createHintsPanel());
        content.add(tabs);
    }

    private JComponent createHintsPanel() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.add(Utility.scrollV("Mapping Hints", mappingHintsList), BorderLayout.CENTER);
        p.add(new JButton(copyMappingAction), BorderLayout.SOUTH);
        return p;
    }

    private JComponent createSelectPanel() {
        JPanel grid = new JPanel(new GridLayout(0, 1, 5, 5));
        grid.add(statsHtml);
        grid.add(recDefHtml);
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.add(grid, BorderLayout.CENTER);
        p.add(new JButton(createMappingAction), BorderLayout.SOUTH);
        return p;
    }

    private class CopyMappingAction extends AbstractAction {
        private CopyMappingAction() {
            super("<html><h3>Use selected mapping hint</h3></html>");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            Object selected = mappingHintsList.getSelectedValue();
            if (selected != null) {
                final NodeMappingEntry nodeMappingEntry = (NodeMappingEntry) selected;
                Exec.work(new Runnable() {
                    @Override
                    public void run() {
                        createModel.createMapping(nodeMappingEntry.getNodeMapping());
                    }
                });
            }
        }
    }

    private class CreateMappingAction extends AbstractAction {
        private static final String CREATE = "<html><h3>Create mapping</h3></html>";
        private static final String EXISTS = "<html><h3>Mapping has been created</h3></html>";
        private static final String SELECT = "<html><h3>Select source and target</h3></html>";

        private CreateMappingAction() {
            super(SELECT);
            setEnabled(!createModel.canCreate());
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            Exec.work(new Runnable() {
                @Override
                public void run() {
                    createModel.createMapping();
                }
            });
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
            else if (createModel.getNodeMappingEntry() != null) {
                setEnabled(false);
                putValue(Action.NAME, EXISTS);
                putValue(Action.SHORT_DESCRIPTION, "<html>The mapping has already been created.");
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
