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

import eu.delving.metadata.CodeGenerator;
import eu.delving.metadata.FieldDefinition;
import eu.delving.metadata.FieldMapping;
import eu.delving.metadata.SourceVariable;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.model.FieldListModel;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.util.List;

/**
 * Pop up to complete a mapping by choosing from a list
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TargetPopup extends FrameBase {

    private JList targetFieldList;
    private Context context;

    public interface Context {
        List<SourceVariable> createSelectedVariableList();
        String getConstantFieldValue();
        void clear();
    }

    public TargetPopup(FrameBase parent, Context context) {
        super(parent, parent.getSipModel(), "Create Mapping", true);
        this.context = context;
        targetFieldList = new JList(sipModel.getUnmappedFieldListModel());
        targetFieldList.setCellRenderer(new FieldListModel.CellRenderer());
        targetFieldList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setDefaultSize(400, 600);
    }

    @Override
    protected void buildContent(Container content) {
        content.add(createCenter(), BorderLayout.CENTER);
        content.add(createSouth(), BorderLayout.SOUTH);
    }

    @Override
    protected void refresh() {
        targetFieldList.clearSelection();
    }

    private JComponent createSouth() {
        JPanel p = new JPanel(new FlowLayout());
        p.add(new JButton(new CreateMappingAction()));
        return p;
    }

    private JComponent createCenter() {
        JPanel p = new JPanel(new GridLayout(0, 1, 5, 5));
        p.setBorder(BorderFactory.createTitledBorder("Unmapped Target Fields"));
        p.add(scroll(targetFieldList));
        return p;
    }

    private class CreateMappingAction extends AbstractAction {

        private CreateMappingAction() {
            super("Create Mapping");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            FieldDefinition fieldDefinition = (FieldDefinition) targetFieldList.getSelectedValue();
            if (fieldDefinition != null) {
                CodeGenerator generator = new CodeGenerator();
                FieldMapping fieldMapping = new FieldMapping(fieldDefinition);
                generator.generateCodeFor(fieldMapping, context.createSelectedVariableList(), context.getConstantFieldValue());
                sipModel.addFieldMapping(fieldMapping);
            }
            context.clear();
            closeFrame();
        }
    }

}
