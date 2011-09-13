/*
 * Copyright 2007 EDL FOUNDATION
 *
 *  Licensed under the EUPL, Version 1.0 or? as soon they
 *  will be approved by the European Commission - subsequent
 *  versions of the EUPL (the "Licence");
 *  you may not use this work except in compliance with the
 *  Licence.
 *  You may obtain a copy of the Licence at:
 *
 *  http://ec.europa.eu/idabc/eupl
 *
 *  Unless required by applicable law or agreed to in
 *  writing, software distributed under the Licence is
 *  distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied.
 *  See the Licence for the specific language governing
 *  permissions and limitations under the Licence.
 */

package eu.delving.sip.menus;

import eu.delving.metadata.RecordMapping;
import eu.delving.sip.model.SipModel;

import javax.swing.AbstractAction;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import java.awt.Component;
import java.awt.event.ActionEvent;

/**
 * The menu for handling files
 *
 * @author Gerald de Jong, Beautiful Code BV, <geralddejong@gmail.com>
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */

public class TemplateMenu extends JMenu {
    private Component parent;
    private SipModel sipModel;

    public TemplateMenu(Component parent, SipModel sipModel) {
        super("Templates");
        this.parent = parent;
        this.sipModel = sipModel;
        refresh();
    }

    private void refresh() {
        this.removeAll();
        this.add(new SaveNewTemplateAction());
        this.addSeparator();
        JMenu updateMenu = new JMenu("Update an existing template");
        this.add(updateMenu);
        JMenu deleteMenu = new JMenu("Delete a template");
        this.add(deleteMenu);
        this.addSeparator();
//        try {
//            Map<String, RecordMapping> templates = sipModel.getStorage().getTemplates(sipModel.getDataSetModel());
            // todo: templates are saved above all datasets but the metadata model is set-specific, a problem.
//            for (Map.Entry<String, RecordMapping> entry : templates.entrySet()) {
//                this.add(new ApplyTemplateAction(entry.getKey(), entry.getValue()));
//                updateMenu.add(new UpdateTemplateAction(entry.getKey()));
//                deleteMenu.add(new DeleteTemplateAction(entry.getKey()));
//            }
//        }
//        catch (StorageException e) {
//            sipModel.getUserNotifier().tellUser("Unable to load template", e);
//        }
    }

    private class SaveNewTemplateAction extends AbstractAction {

        private SaveNewTemplateAction() {
            super("Save current mapping as new template");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            String name = JOptionPane.showInputDialog(parent, "What name should this template be given");
            if (name != null && !name.isEmpty()) {
//                sipModel.saveAsTemplate(name);
                refresh();
            }
        }
    }

    private class UpdateTemplateAction extends AbstractAction {
        private String prefix;

        private UpdateTemplateAction(String prefix) {
            super(String.format("Update %s mapping template with the current one", prefix));
            this.prefix = prefix;
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
//            sipModel.saveAsTemplate(prefix);
            refresh();
        }
    }

    private class ApplyTemplateAction extends AbstractAction {
        private String name;
        private RecordMapping recordMapping;

        private ApplyTemplateAction(String name, RecordMapping recordMapping) {
            super(String.format("Apply the %s template", name));
            this.name = name;
            this.recordMapping = recordMapping;
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
//            sipModel.applyTemplate(recordMapping);
        }
    }

    private class DeleteTemplateAction extends AbstractAction {
        private String name;

        private DeleteTemplateAction(String name) {
            super(String.format("Delete %s", name));
            this.name = name;
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
//            try {
//                sipModel.getStorage().deleteTemplate(name);
//            }
//            catch (StorageException e) {
//                sipModel.getUserNotifier().tellUser("Unable to delete template", e);
//            }
            refresh();
        }
    }
}