/*
 * Copyright 2011, 2012 Delving BV
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

import com.jgoodies.forms.builder.PanelBuilder;
import com.jgoodies.forms.factories.Borders;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import eu.delving.schema.SchemaRepository;
import eu.delving.schema.SchemaType;
import eu.delving.schema.SchemaVersion;
import eu.delving.schema.xml.Schema;
import eu.delving.schema.xml.Version;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.SwingHelper;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

import static eu.delving.sip.base.KeystrokeHelper.SPACE;
import static eu.delving.sip.base.KeystrokeHelper.addKeyboardAction;
import static eu.delving.sip.base.SwingHelper.scrollV;

/**
 * Provide an form interface for creating datasets
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class DataSetStandaloneFrame extends FrameBase {
    private static final Font MONOSPACED = new Font("Monospaced", Font.BOLD, 26);
    private static final Pattern SPEC_PATTERN = Pattern.compile("[A-Za-z0-9-]{3,40}");
    private static final String UNSELECTED = "<select>";
    private static final FactDefinition SCHEMA_VERSIONS_FACT = new FactDefinition("schemaVersions", "Schema Versions");
    private static final JLabel EMPTY_LABEL = new JLabel("Fetching...", JLabel.CENTER);
    private static final String FACTS_PREFIX = "facts";
    private SchemaRepository schemaRepository;
    private List<FactDefinition> factDefinitions;
    private Map<String, FieldComponent> fieldComponents = new TreeMap<String, FieldComponent>();
    private DataSetListModel listModel = new DataSetListModel();
    private JList dataSetList = new JList(listModel);
    private JPanel fieldPanel = new JPanel();
    private EditAction editAction = new EditAction();

    public DataSetStandaloneFrame(SipModel sipModel, SchemaRepository schemaRepository) {
        super(Which.DATA_SET, sipModel, "Data set facts");
        this.schemaRepository = schemaRepository;
        fieldPanel.add(EMPTY_LABEL);
        sipModel.exec(createFactDefFetcher());
        dataSetList.setFont(MONOSPACED);
        dataSetList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        dataSetList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent listSelectionEvent) {
                if (listSelectionEvent.getValueIsAdjusting()) return;
                DataSet dataSet = (DataSet) dataSetList.getSelectedValue();
                Map<String, String> facts = dataSet == null ? null : dataSet.getDataSetFacts();
                setFacts(facts);
                editAction.setEnabled(dataSet != null);
            }
        });
        dataSetList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() > 1 && editAction.isEnabled()) {
                    if (dataSetList.getSelectedIndex() != dataSetList.locationToIndex(e.getPoint())) return;
                    editAction.actionPerformed(null);
                }
            }
        });
        addKeyboardAction(editAction, SPACE, (JComponent) getContentPane());
    }

    @Override
    protected void buildContent(Container content) {
        fieldPanel.setBorder(BorderFactory.createTitledBorder("Facts"));
        content.add(createRight(), BorderLayout.EAST);
        content.add(createCenter(), BorderLayout.CENTER);
        sipModel.exec(createFormBuilder());
        listModel.refresh();
    }

    private JPanel createCenter() {
        JPanel p = new JPanel(new BorderLayout());
        p.add(scrollV("Data sets", dataSetList), BorderLayout.CENTER);
        p.add(new JButton(editAction), BorderLayout.SOUTH);
        return p;
    }

    private JPanel createRight() {
        JPanel p = new JPanel(new BorderLayout());
        p.add(fieldPanel, BorderLayout.CENTER);
        p.add(new JButton(new CreateAction()), BorderLayout.SOUTH);
        return p;
    }

    private Work createFactDefFetcher() {
        return new Work() {

            @Override
            public Job getJob() {
                return Job.FETCH_FACTS_DEF;
            }

            @Override
            public void run() {
                try {
                    String factsString = schemaRepository.getSchema(new SchemaVersion(FACTS_PREFIX, "1.0.0"), SchemaType.FACT_DEFINITIONS);
                    final XStream xstream = new XStream();
                    xstream.processAnnotations(FactDefinitionList.class);
                    FactDefinitionList factDefinitionList = (FactDefinitionList) xstream.fromXML(factsString);
                    factDefinitions = new ArrayList<FactDefinition>();
                    factDefinitions.addAll(factDefinitionList.definitions);
                    factDefinitions.add(SCHEMA_VERSIONS_FACT);
                }
                catch (IOException e) {
                    sipModel.getFeedback().alert("Unable to fetch dataset facts", e);
                }
            }
        };
    }

    private Swing createFormBuilder() {
        return new Swing() {
            @Override
            public void run() {
                fieldPanel.removeAll();
                FormLayout layout = new FormLayout(
                        "right:pref, 3dlu, pref",
                        createRowLayout(factDefinitions)
                );
                PanelBuilder pb = new PanelBuilder(layout);
                pb.border(Borders.DIALOG);
                CellConstraints cc = new CellConstraints();
                int count = 2;
                for (FactDefinition factDefinition : factDefinitions) {
                    pb.addLabel(factDefinition.prompt, cc.xy(1, count));
                    if (factDefinition.options == null) {
                        JTextField field = new JTextField(30);
                        pb.add(field, cc.xy(3, count));
                        if (factDefinition == SCHEMA_VERSIONS_FACT) {
                            field.setToolTipText(createSchemaVersionToolTip());
                        }
                        fieldComponents.put(factDefinition.name, new FieldComponent(field));
                    }
                    else {
                        JComboBox comboBox = new JComboBox(factDefinition.getOptions());
                        pb.add(comboBox, cc.xy(3, count));
                        fieldComponents.put(factDefinition.name, new FieldComponent(comboBox));
                    }
                    count += 2;
                }
                fieldPanel.add(pb.getPanel(), BorderLayout.CENTER);
                fieldPanel.revalidate();
            }

        };
    }

    private String createSchemaVersionToolTip() {
        List<SchemaVersion> list = new ArrayList<SchemaVersion>();
        for (Schema schema : schemaRepository.getSchemas()) {
            if (FACTS_PREFIX.equals(schema.prefix)) continue;
            for (Version version : schema.versions) {
                list.add(new SchemaVersion(schema.prefix, version.number));
            }
        }
        StringBuilder out = new StringBuilder("<html><h2>Schema Version Choices:</h2>\n");
        out.append("<p>This field must be comma-separated list<br>of the items below.</p>\n");
        out.append("<ul>\n");
        for (SchemaVersion schemaVersion : list) {
            out.append("<li>").append(schemaVersion).append("</li>\n");
        }
        out.append("</ul>\n</html>");
        return out.toString();
    }

    private String createRowLayout(List<FactDefinition> factDefinitions) {
        StringBuilder out = new StringBuilder("3dlu");
        int size = factDefinitions.size();
        while (size-- > 0) out.append(", pref, 3dlu");
        return out.toString();
    }

    private class FieldComponent {
        private JTextField textField;
        private JComboBox comboBox;

        private FieldComponent(JTextField textField) {
            this.textField = textField;
            this.textField.setEnabled(false);
        }

        private FieldComponent(JComboBox comboBox) {
            this.comboBox = comboBox;
            this.comboBox.setEnabled(false);
        }

        public boolean isTextField() {
            return textField != null;
        }

        public String getValue() {
            return isTextField() ? textField.getText().trim() : (String) comboBox.getSelectedItem();
        }

        public void setValue(String value) {
            if (isTextField()) {
                textField.setText(value);
            }
            else {
                if (value.isEmpty()) value = UNSELECTED;
                comboBox.setSelectedItem(value);
            }
        }

        public void setEnabled(boolean enabled) {
            if (isTextField()) {
                textField.setEnabled(enabled);
            }
            else {
                comboBox.setEnabled(enabled);
            }
        }

        public boolean isValid() {
            if (isTextField()) {
                return !getValue().isEmpty();
            }
            else {
                return !getValue().equals(UNSELECTED);
            }
        }

        public void requestFocus() {
            if (isTextField()) {
                textField.requestFocus();
            }
            else {
                comboBox.requestFocus();
            }
        }
    }

    private Map<String, String> getFacts() {
        Map<String, String> facts = new TreeMap<String, String>();
        for (FactDefinition factDefinition : factDefinitions) {
            FieldComponent fieldComponent = fieldComponents.get(factDefinition.name);
            if (fieldComponent.isValid()) {
                facts.put(factDefinition.name, fieldComponent.getValue());
            }
            else {
                sipModel.getFeedback().alert(String.format(
                        "Field '%s' contains invalid value '%s'", factDefinition.prompt, fieldComponent.getValue()
                ));
                return null;
            }
        }
        return facts;
    }

    private void clearFacts(String spec) {
        setFacts(null);
        fieldComponents.get("spec").setValue(spec);
        fieldComponents.get(factDefinitions.get(1).name).requestFocus();
    }

    private void setFacts(Map<String, String> facts) {
        if (factDefinitions == null) return;
        boolean first = true;
        for (FactDefinition factDefinition : factDefinitions) {
            FieldComponent fieldComponent = fieldComponents.get(factDefinition.name);
            if (fieldComponent == null) throw new RuntimeException("No field component for " + factDefinition.name);
            String value = facts == null ? "" : facts.get(factDefinition.name);
            if (value == null) value = "";
            fieldComponent.setValue(value);
            fieldComponent.setEnabled(facts == null && !first);
            first = false;
        }
    }

    @XStreamAlias("fact-definition-list")
    public static class FactDefinitionList {
        @XStreamImplicit
        public List<FactDefinition> definitions;
    }

    @XStreamAlias("fact-definition")
    public static class FactDefinition {

        @XStreamAsAttribute
        public String name;

        public String prompt;

        public List<String> options;

        public FactDefinition() {
        }

        public FactDefinition(String name, String prompt) {
            this.name = name;
            this.prompt = prompt;
        }

        public String[] getOptions() {
            String[] array = new String[options.size() + 1];
            int index = 0;
            array[index++] = UNSELECTED;
            for (String option : options) array[index++] = option;
            return array;
        }
    }

    private class DataSetListModel extends AbstractListModel {
        private List<DataSet> dataSets;

        public void refresh() {
            List<DataSet> freshDataSets = new ArrayList<DataSet>();
            freshDataSets.addAll(sipModel.getStorage().getDataSets().values());
            Collections.sort(freshDataSets);
            if (getSize() > 0) {
                int sizeWas = getSize();
                dataSets = null;
                fireIntervalRemoved(this, 0, sizeWas);
            }
            dataSets = freshDataSets;
            fireIntervalAdded(this, 0, getSize());
        }

        public boolean exists(String spec) {
            if (dataSets != null) for (DataSet dataSet : dataSets) {
                if (spec.equals(dataSet.getSpec())) return true;
            }
            return false;
        }

        @Override
        public int getSize() {
            return dataSets == null ? 0 : dataSets.size();
        }

        @Override
        public Object getElementAt(int i) {
            return dataSets == null ? null : dataSets.get(i);
        }

        public int indexOf(DataSet dataSet) {
            int index = 0;
            if (dataSets != null) for (DataSet member : dataSets) {
                if (dataSet.getSpec().equals(member.getSpec())) return index;
                index++;
            }
            return -1;
        }
    }

    private class CreateAction extends AbstractAction {
        private String spec;

        private CreateAction() {
            refresh();
        }

        public void refresh() {
            if (spec == null) {
                putValue(Action.NAME, "<html><h2>Create new data set</h2>");
            }
            else {
                putValue(Action.NAME, String.format("<html><h2>Save data set '%s'</h2>", spec));
            }
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            if (spec == null) {
                String chosenSpec = sipModel.getFeedback().ask("Please enter a spec for the new data set. Use only letters and numbers.");
                if (chosenSpec != null) {
                    if (!SPEC_PATTERN.matcher(chosenSpec).matches()) {
                        sipModel.getFeedback().alert(String.format(
                                "The spec '%s' is not acceptable, since it must match the regular expression /%s/.",
                                chosenSpec, SPEC_PATTERN
                        ));
                        return;
                    }
                    if (listModel.exists(chosenSpec)) {
                        sipModel.getFeedback().alert(String.format("The spec '%s' already exists.", chosenSpec));
                        return;
                    }
                    spec = chosenSpec;
                    clearFacts(spec);
                    refresh();
                }
            }
            else {
                try {
                    DataSet dataSet = sipModel.getStorage().createDataSet(spec, "standalone");
                    Map<String, String> facts = getFacts();
                    if (facts == null) return;
                    dataSet.setDataSetFacts(facts);
                    listModel.refresh();
                    int index = listModel.indexOf(dataSet);
                    dataSetList.setSelectedIndex(index);
                    spec = null;
                    refresh();
                }
                catch (StorageException e) {
                    sipModel.getFeedback().alert(String.format("Unable to create data set with spec '%s'.", spec));
                }
            }
        }
    }

    private class EditAction extends AbstractAction {

        private EditAction() {
            super("Select this data set for editing");
            putValue(Action.SMALL_ICON, SwingHelper.ICON_EDIT);
            setEnabled(false);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            DataSet dataSet = (DataSet) dataSetList.getSelectedValue();
            if (dataSet == null) return;
            List<SchemaVersion> schemaVersions = dataSet.getSchemaVersions();
            if (schemaVersions == null || schemaVersions.isEmpty()) return;
            setEnabled(false);
            String prefix;
            if (schemaVersions.size() == 1) {
                prefix = schemaVersions.get(0).getPrefix();
            }
            else {
                prefix = askForPrefix(schemaVersions);
                if (prefix == null) return;
            }
            sipModel.setDataSetPrefix(dataSet, prefix, new Swing() {
                @Override
                public void run() {
                    setEnabled(true);
                    sipModel.getViewSelector().selectView(AllFrames.View.QUICK_MAPPING);
                }
            });
        }

        private String askForPrefix(List<SchemaVersion> schemaVersions) {
            JPanel buttonPanel = new JPanel(new GridLayout(0, 1));
            ButtonGroup buttonGroup = new ButtonGroup();
            for (SchemaVersion schemaVersion : schemaVersions) {
                JRadioButton b = new JRadioButton(schemaVersion.getPrefix() + " mapping");
                if (buttonGroup.getButtonCount() == 0) b.setSelected(true);
                b.setActionCommand(schemaVersion.getPrefix());
                buttonGroup.add(b);
                buttonPanel.add(b);
            }
            return sipModel.getFeedback().form("Choose Schema", buttonPanel) ? buttonGroup.getSelection().getActionCommand() : null;
        }
    }


}
