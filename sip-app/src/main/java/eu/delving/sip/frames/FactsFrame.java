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
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.Work;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.List;

/**
 * Provide an form interface for creating datasets
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class FactsFrame extends FrameBase {
    public static final JLabel EMPTY_LABEL = new JLabel("Fetching...", JLabel.CENTER);
    private SchemaRepository schemaRepository;
    private boolean fetching;
    private JPanel center = new JPanel();

    public FactsFrame(SipModel sipModel, SchemaRepository schemaRepository) {
        super(Which.FACTS, sipModel, "Data set facts");
        this.schemaRepository = schemaRepository;
    }

    @Override
    protected void buildContent(Container content) {
        center.add(EMPTY_LABEL);
        content.add(center, BorderLayout.CENTER);
    }

    @Override
    protected void onOpen(boolean opened) {
        if (!fetching) {
            fetching = true;
            sipModel.exec(new Work() {

                @Override
                public Job getJob() {
                    return Job.FETCH_FACTS_DEF;
                }

                @Override
                public void run() {
                    try {
                        String factsString = schemaRepository.getSchema(new SchemaVersion("facts", "1.0.0"), SchemaType.FACT_DEFINITIONS);
                        final XStream xstream = new XStream();
                        xstream.processAnnotations(FactDefinitionList.class);
                        FactDefinitionList factDefinitionList = (FactDefinitionList) xstream.fromXML(factsString);
                        sipModel.exec(createFormBuilder(factDefinitionList));
                    }
                    catch (IOException e) {
                        sipModel.getFeedback().alert("Unable to fetch dataset facts", e);
                    }
                }
            });
        }
    }

    private Swing createFormBuilder(final FactDefinitionList factDefinitionList) {
        return new Swing() {
            @Override
            public void run() {
                center.removeAll();
                FormLayout layout = new FormLayout(
                        "right:pref, 3dlu, pref",
                        createRowLayout(factDefinitionList)
                );
                PanelBuilder pb = new PanelBuilder(layout);
                pb.border(Borders.DIALOG);
                CellConstraints cc = new CellConstraints();
                int count = 2;
                for (FactDefinition factDefinition : factDefinitionList.definitions) {
                    pb.addLabel(factDefinition.prompt, cc.xy(1, count));
                    if (factDefinition.options == null) {
                        JTextField field = new JTextField(30);
                        pb.add(field, cc.xy(3, count));
                    }
                    else {
                        JComboBox box = new JComboBox(factDefinition.options.toArray());
                        pb.add(box, cc.xy(3, count));
                    }
                    count += 2;
                }
                center.add(pb.getPanel(), BorderLayout.CENTER);
                center.revalidate();
            }

        };
    }

    private String createRowLayout(FactDefinitionList factDefinitionList) {
        StringBuilder out = new StringBuilder("3dlu");
        for (FactDefinition factDefinition : factDefinitionList.definitions) {
            out.append(", pref, 3dlu");
        }
        return out.toString();
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
    }
}
