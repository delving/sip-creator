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
import eu.delving.sip.base.HtmlPanel;
import eu.delving.sip.base.StatsTreeNode;
import eu.delving.sip.model.CreateModel;
import eu.delving.sip.model.RecDefTreeNode;
import eu.delving.sip.model.SipModel;

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
    private HtmlPanel statsHtml = new HtmlPanel("Input");
    private HtmlPanel recDefHtml = new HtmlPanel("Output");
    private CreateMappingAction createMappingAction = new CreateMappingAction();

    public CreateFrame(JDesktopPane desktop, SipModel sipModel) {
        super(desktop, sipModel, "Create", false);
        statsHtml.setHtml(SELECT_STATS);
        recDefHtml.setHtml(SELECT_RECDEF);
        createMappingAction.setEnabled(false);
        sipModel.getCreateModel().addListener(new CreateModel.Listener() {
            @Override
            public void statsTreeNodeSet(CreateModel createModel) {
                SortedSet<StatsTreeNode> statsTreeNodes = createModel.getStatsTreeNodes();
                if (statsTreeNodes == null) {
                    statsHtml.setHtml(SELECT_STATS);
                }
                else {
                    StringBuilder out = new StringBuilder("<html><table>");
                    for (StatsTreeNode node : statsTreeNodes) {
                        out.append("<tr><td>");
                        out.append(node.toHtmlChunk());
                        out.append("</td></tr>");
                    }
                    out.append("</table></html>");
                    statsHtml.setHtml(out.toString());
                }
                createMappingAction.handleEnablement();
            }

            @Override
            public void recDefTreeNodeSet(CreateModel createModel) {
                RecDefTreeNode recDefTreeNode = createModel.getRecDefTreeNode();
                recDefHtml.setHtml(recDefTreeNode != null ? recDefTreeNode.toHtml() : SELECT_RECDEF);
                createMappingAction.handleEnablement();
            }

            @Override
            public void nodeMappingSet(CreateModel createModel) {
                createMappingAction.handleEnablement();
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

    private class CreateMappingAction extends AbstractAction {
        private static final String CREATE = "<html><h3>Create mapping</h3></html>";
        private static final String SELECT = "<html><h3>Select source and target</h3></html>";
        private static final String EXISTS = "<html><h3>Mapping has been created</h3></html>";

        private CreateMappingAction() {
            super(SELECT);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            Exec.work(new Runnable() {
                @Override
                public void run() {
                    sipModel.getCreateModel().createMapping();
                }
            });
        }

        public void handleEnablement() {
            if (sipModel.getCreateModel().canCreate()) {
                setEnabled(true);
                StringBuilder tooltip = new StringBuilder("<html><table cellpadding=10><tr><td><h3>From:</h3><ul>");
                for (StatsTreeNode node : sipModel.getCreateModel().getStatsTreeNodes()) {
                    tooltip.append("<li>").append(node.getPath(false).toString()).append("</li>");
                }
                tooltip.append("</ul><h3>To:</h3><ul>");
                tooltip.append("<li>").append(sipModel.getCreateModel().getRecDefTreeNode().getRecDefPath().getTagPath().toString()).append("</li>");
                tooltip.append("</ul></td></tr></table></html>");
                putValue(Action.NAME, CREATE);
                putValue(Action.SHORT_DESCRIPTION, tooltip.toString());
            }
            else if (sipModel.getCreateModel().getNodeMapping() != null) {
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
