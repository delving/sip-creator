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

import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.HtmlPanel;
import eu.delving.sip.base.StatsTreeNode;
import eu.delving.sip.model.CreateModel;
import eu.delving.sip.model.RecDefTreeNode;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * Choices have been made in the trees, now we actually make the mapping
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class CreateFrame extends FrameBase {
    private HtmlPanel statsHtml = new HtmlPanel("Input");
    private HtmlPanel recDefHtml = new HtmlPanel("Output");
    private CreateMappingAction createMappingAction = new CreateMappingAction();
    private CancelAction cancelAction = new CancelAction();

    public CreateFrame(JDesktopPane desktop, SipModel sipModel) {
        super(desktop, sipModel, "Create", false);
        setStatsTreeNode(null);
        setRecDefTreeNode(null);
        createMappingAction.setEnabled(false);
        sipModel.getCreateModel().addListener(new CreateModel.Listener() {
            @Override
            public void statsTreeNodeSet(CreateModel createModel) {
                setStatsTreeNode(createModel.getStatsTreeNode());
            }

            @Override
            public void recDefTreeNodeSet(CreateModel createModel) {
                setNode(createModel.getRecDefTreeNode());
            }

            @Override
            public void nodeMappingSet(CreateModel createModel) {
                // todo: implement
            }
        });
//        getActionMap().put(createMappingAction.getValue(Action.NAME), createMappingAction);
//        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(' '), createMappingAction.getValue(Action.NAME));
    }

    private void setStatsTreeNode(StatsTreeNode statsTreeNode) {
        statsHtml.setHtml(statsTreeNode != null ? statsTreeNode.toHtml() : "<html><h1>Select</h1></html>");
        checkEnableCreate();
    }

    private void setRecDefTreeNode(RecDefTreeNode recDefTreeNode) {
        setNode(recDefTreeNode);
    }

    private void setNode(RecDefTreeNode recDefTreeNode) {
        recDefHtml.setHtml(recDefTreeNode != null ? recDefTreeNode.toHtml() : "<html><h1>Select</h1></html>");
        checkEnableCreate();
    }

    private void checkEnableCreate() {
        createMappingAction.setEnabled(sipModel.getCreateModel().canCreate());
    }

    @Override
    protected void buildContent(Container content) {
        content.add(createPanel(), BorderLayout.CENTER);
        content.add(createButtonPanel(), BorderLayout.SOUTH);
    }

    @Override
    protected void refresh() {
    }

    private JComponent createPanel() {
        JPanel p = new JPanel(new GridLayout(1, 0, 5, 5));
        p.add(statsHtml);
        p.add(recDefHtml);
        return p;
    }

    private JComponent createButtonPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        p.add(new JButton(cancelAction));
        p.add(new JButton(createMappingAction));
        return p;
    }

    private class CreateMappingAction extends AbstractAction {

        private CreateMappingAction() {
            super("Create Mapping");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            sipModel.getCreateModel().createMapping();
        }
    }

    private class CancelAction extends AbstractAction {

        private CancelAction() {
            super("Cancel");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {

        }
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(600, 440);
    }
}
