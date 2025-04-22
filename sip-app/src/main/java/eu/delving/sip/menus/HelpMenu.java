/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package eu.delving.sip.menus;

import eu.delving.sip.Application;
import eu.delving.sip.frames.AllFrames;
import eu.delving.sip.model.SipModel;
import org.apache.commons.lang.StringEscapeUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static eu.delving.sip.files.Storage.TELEMETRY_ENABLED;

/**
 * A menu for help-related options and actions.
 *
 */
public class HelpMenu extends JMenu {
    private final SipModel sipModel;
    private final AllFrames allFrames;

    public HelpMenu(SipModel sipModel, AllFrames allFrames, boolean isTelemetryIncluded) {
        super("Help");
        this.sipModel = sipModel;
        this.allFrames = allFrames;
        if (isTelemetryIncluded) {
            add(new TelemetryMenuItem());
            add(new JSeparator());
        }
        add(new LicensesMenuItem());
        add(new JSeparator());
        add(new AboutMenuItem());
    }

    private class TelemetryMenuItem extends JCheckBoxMenuItem implements ItemListener {
        private final boolean isTelemetryEnabled;

        public TelemetryMenuItem() {
            super("Enable Telemetry");
            isTelemetryEnabled = sipModel.getAppPreferences().getProperty(TELEMETRY_ENABLED, "false").contentEquals("true");
            setSelected(isTelemetryEnabled);
            addItemListener(this);
        }

        @Override
        public void itemStateChanged(ItemEvent e) {
            int state = e.getStateChange();
            //boolean selected = state == ItemEvent.SELECTED;

            // Clear the telemetry setting
            sipModel.getAppPreferences().remove(TELEMETRY_ENABLED);
            sipModel.saveAppProperties();
            JOptionPane.showMessageDialog(sipModel.getDesktop(), "Please restart the application now to change the telemetry setting.",
                    "Telemetry", JOptionPane.INFORMATION_MESSAGE);

            // Restore the selections state to the original setting
            removeItemListener(this);
            setState(isTelemetryEnabled);
            addItemListener(this);
        }
    }

    private class LicensesMenuItem extends JMenuItem implements ActionListener {
        final static String actionTitle = "Licenses";

        public LicensesMenuItem() {
            super(actionTitle);
            addActionListener(this);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            class DependencyLicense implements Comparable<DependencyLicense> {
                final String name;
                NodeList licenses;

                DependencyLicense(Node node) {
                    NodeList children = node.getChildNodes();
                    Map<String, String> props = new HashMap<>();
                    for (int i = 0; i < children.getLength(); i++) {
                        Node child = children.item(i);
                        switch (child.getNodeName()) {
                            case "groupId":
                            case "artifactId":
                            case "version":
                                props.put(child.getNodeName(), child.getTextContent());
                                break;
                            case "licenses":
                                licenses = child.getChildNodes();
                                break;
                        }
                    }
                    name = props.getOrDefault("groupId", "")
                        + ":" + props.getOrDefault("artifactId", "")
                        + ":" + props.getOrDefault("version", "");
                }

                String toHtml() {
                    StringBuilder html = new StringBuilder();
                    html.append("<html><body>");
                    for (int i = 0; i < licenses.getLength(); i++) {
                        Node node = licenses.item(i);
                        if (node.getNodeType() != Node.ELEMENT_NODE) continue;
                        NodeList children = node.getChildNodes();
                        Map<String, String> props = new HashMap<>();
                        for (int j = 0; j < children.getLength(); j++) {
                            Node child = children.item(j);
                            props.put(child.getNodeName(), child.getTextContent());
                        }
                        html.append("<b>");
                        html.append(StringEscapeUtils.escapeHtml(props.getOrDefault("name", "(name)")));
                        html.append("</b><br>");
                        String url = props.get("url");
                        if (url != null) {
                            String escaped = StringEscapeUtils.escapeHtml(url);
                            html.append("<a href=\"");
                            html.append(escaped);
                            html.append("\">");
                            html.append(escaped);
                            html.append("</a><br>");
                        }
                    }
                    return html.toString();
                }

                @Override
                public String toString() {
                    return name;
                }

                @Override
                public int compareTo(DependencyLicense o) {
                    return name.compareTo(o.name);
                }
            }
            try {
                DefaultListModel<DependencyLicense> libraryListModel = new DefaultListModel<>();
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                URL resource = Application.class.getResource("/licenses.xml");
                if (resource == null) {
                    JOptionPane.showMessageDialog(sipModel.getDesktop(),
                        "licenses.xml not found.",
                        actionTitle,
                        JOptionPane.WARNING_MESSAGE);
                    return;
                }
                Document document = builder.parse(new InputSource(resource.openStream()));
                NodeList dependencies = document.getElementsByTagName("dependency");
                List<DependencyLicense> dependencyLicenseList = new ArrayList<>();
                for (int i = 0; i < dependencies.getLength(); i++) {
                    dependencyLicenseList.add(new DependencyLicense(dependencies.item(i)));
                }
                libraryListModel.addAll(dependencyLicenseList.stream().sorted().toList());
                JList<DependencyLicense> libraryList = new JList<>(libraryListModel);
                libraryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
                JScrollPane scrollLibraryList = new JScrollPane(libraryList,
                    JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                    JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
                scrollLibraryList.setPreferredSize(new Dimension(600, 300));
                JLabel libraryLabel = new JLabel("Libraries:");
                libraryLabel.setBorder(new EmptyBorder(0, 0, 5, 0));
                final String noSelectionText = "Select a library.";
                JEditorPane editorPane = new JEditorPane("text/html", noSelectionText);
                editorPane.setEditable(false);
                editorPane.addHyperlinkListener(e2 -> {
                    if (e2.getEventType().equals(HyperlinkEvent.EventType.ACTIVATED)) {
                        try {
                            Desktop.getDesktop().browse(e2.getURL().toURI());
                        } catch (IOException | URISyntaxException ex) {
                            // Ignore
                        }
                    }
                });
                JScrollPane scrollEditorPane = new JScrollPane(editorPane,
                    JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                    JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
                scrollEditorPane.setPreferredSize(new Dimension(600, 100));
                libraryList.addListSelectionListener(e1 -> {
                    DependencyLicense selected = libraryList.getSelectedValue();
                    editorPane.setText(selected != null ? selected.toHtml() : noSelectionText);
                });
                JPanel message = new JPanel();
                message.setLayout(new GridBagLayout());
                GridBagConstraints constraints = new GridBagConstraints();
                constraints.fill = GridBagConstraints.HORIZONTAL;
                constraints.weightx = 1;
                constraints.gridx = 0;
                message.add(libraryLabel, constraints);
                message.add(scrollLibraryList, constraints);
                message.add(scrollEditorPane, constraints);
                Object[] options = { "OK" };
                JOptionPane.showOptionDialog(sipModel.getDesktop(),
                    message,
                    actionTitle,
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    options,
                    options[0]);
            } catch (ParserConfigurationException ex) {
                throw new RuntimeException(ex);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            } catch (SAXException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private class AboutMenuItem extends JMenuItem implements ActionListener {
        final static String actionTitle = "About";

        public AboutMenuItem() {
            super(actionTitle);
            addActionListener(this);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            String text = """
<h1>%s</h1>
%s<br><br>
<b>Copyright 2011-2025 Delving BV</b><br>
<br>
Licensed under the EUPL, Version 1.2 or – as soon they will be approved by<br>
the European Commission – subsequent versions of the EUPL (the "Licence");<br>
You may not use this work except in compliance with the Licence.<br>
You may obtain a copy of the Licence at:<br>
<br>
<a href="https://joinup.ec.europa.eu/software/page/eupl">https://joinup.ec.europa.eu/software/page/eupl</a><br>
<br>
Unless required by applicable law or agreed to in writing, software<br>
distributed under the Licence is distributed on an "AS IS" basis,<br>
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.<br>
See the Licence for the specific language governing permissions and<br>
limitations under the Licence.""";
            text = String.format(text,
                StringEscapeUtils.escapeHtml(Application.titleString()),
                StringEscapeUtils.escapeHtml(Application.buildString())
            );
            ImageIcon logo = new ImageIcon(getClass().getResource("/sip-creator-logo.png"));

            JEditorPane editorPane = new JEditorPane("text/html", text);
            editorPane.setEditable(false);
            editorPane.addHyperlinkListener(e1 -> {
                if (e1.getEventType().equals(HyperlinkEvent.EventType.ACTIVATED)) {
                    try {
                        Desktop.getDesktop().browse(e1.getURL().toURI());
                    } catch (IOException | URISyntaxException ex) {
                        // Ignore
                    }
                }
            });
            JOptionPane.showMessageDialog(sipModel.getDesktop(), editorPane,actionTitle,
                    JOptionPane.PLAIN_MESSAGE, logo);
        }
    }

}
