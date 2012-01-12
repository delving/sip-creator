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
    private StatsTreeNode statsTreeNode;
    private HtmlPanel statsHtml = new HtmlPanel("Input");
    private RecDefTreeNode recDefTreeNode;
    private HtmlPanel recDefHtml = new HtmlPanel("Output");
    private CreateMappingAction createMappingAction = new CreateMappingAction();
    private CancelAction cancelAction = new CancelAction();

    public CreateFrame(JDesktopPane desktop, SipModel sipModel) {
        super(desktop, sipModel, "Create", false);
        setStatsTreeNode(null);
        setRecDefNode(null);
        createMappingAction.setEnabled(false);
//        getActionMap().put(createMappingAction.getValue(Action.NAME), createMappingAction);
//        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(' '), createMappingAction.getValue(Action.NAME));
    }

    public void setStatsTreeNode(StatsTreeNode statsTreeNode) {
        this.statsTreeNode = statsTreeNode;
        statsHtml.setHtml(statsTreeNode != null ? statsTreeNode.toHtml() : "<html><h1>Select</h1></html>");
        checkEnableCreate();
    }

    public void setRecDefNode(RecDefTreeNode recDefTreeNode) {
        this.recDefTreeNode = recDefTreeNode;
        recDefHtml.setHtml(recDefTreeNode != null ? recDefTreeNode.toHtml() : "<html><h1>Select</h1></html>");
        checkEnableCreate();
    }

    private void checkEnableCreate() {
        createMappingAction.setEnabled(statsTreeNode != null && recDefTreeNode != null);
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
            if (recDefTreeNode == null) throw new RuntimeException();
            recDefTreeNode.addStatsTreeNode(statsTreeNode);
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
