/*
 * Copyright 2011, 2012 Delving BV
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

package eu.delving.sip.base;

import eu.delving.metadata.*;
import eu.delving.sip.model.SourceTreeNode;
import eu.delving.stats.Stats;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.Map;

/**
 * Gathering together a number of things that are done with the Swing library from many parts of the code.
 *
 *
 */

public class SwingHelper {
    public static Color NORMAL_BG = new Color(255, 255, 255);
    public static Color NORMAL_FG = new Color(0, 0, 0);
    public static Color ERROR_BG = new Color(255, 200, 200);
    public static Color NOT_EDITABLE_BG = new Color(255, 255, 200);
    public static Color DELIMITER_BG = new Color(255, 255, 200);
    public static Color MAPPED_COLOR = new Color(220, 255, 220);
    public static Color FACT_COLOR = new Color(212, 195, 255);
    public static Color CONSTANT_COLOR = new Color(186, 245, 255);
    public static Color HIGHLIGHTED_COLOR = new Color(112, 187, 212);
    public static Color LONG_TERM_JOB_COLOR = new Color(255, 180, 180);
    public static Color NORMAL_JOB_COLOR = new Color(200, 255, 200);
    public static Color REPORT_ERROR = new Color(50, 0, 0);

    public static final Icon ICON_VALUE = icon("value");
    public static final Icon ICON_COMPOSITE = icon("composite");
    public static final Icon ICON_ATTRIBUTE = icon("attribute");
    public static final Icon ICON_UNMAPPABLE = icon("unmappable");
    public static final Icon ICON_REWIND = icon("rewind");
    public static final Icon ICON_PLAY = icon("play");
    public static final Icon ICON_VALIDATE = icon("validate");
    public static final Icon ICON_UNLOCK = icon("unlock");
    public static final Icon ICON_DOWNLOAD = icon("download");
    public static final Icon ICON_UPLOAD = icon("upload");
    public static final Icon ICON_FETCH_LIST = icon("fetch-list");
    public static final Icon ICON_EDIT = icon("edit");

    private static Icon icon(String resource) {
        String name = "/icons/" + resource + ".png";
        URL url = SwingHelper.class.getResource(name);
        if (url == null) throw new RuntimeException("Cannot find " + name);
        return new ImageIcon(url);
    }

    public static void setEditable(JTextComponent component, boolean editable) {
        component.setEditable(editable);
        component.setBackground(editable ? NORMAL_BG : NOT_EDITABLE_BG);
    }

    public static void setError(JTextComponent component, boolean error) {
        component.setBackground(error ? ERROR_BG : NORMAL_BG);
    }

    public static void setDelimitedColor(JComponent component, boolean selected) {
        component.setForeground(selected ? DELIMITER_BG : NORMAL_FG);
        component.setBackground(selected ? NORMAL_BG : DELIMITER_BG);
    }

    public static void setSourceNodeColor(JComponent component, SourceTreeNode node, boolean selected, Color normalColor) {
        if (node.getNodeMappings().isEmpty()) {
            normalColor = normalColor.brighter();
        }
        Color color = node.isHighlighted() ? HIGHLIGHTED_COLOR : normalColor;
        component.setOpaque(!selected);
        component.setForeground(selected ? color : NORMAL_FG);
        component.setBackground(selected ? NORMAL_BG : color);
    }

    public static JComponent scrollCodeVH(RSyntaxTextArea content) {
        RTextScrollPane scroll = new RTextScrollPane(content);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scroll.setLineNumbersEnabled(true);
        return scroll;
    }

    public static JComponent scrollVH(JComponent content) {
        JScrollPane scroll = new JScrollPane(content);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        return scroll;
    }

    public static JComponent scrollVH(String title, JComponent content) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        JScrollPane scroll = new JScrollPane(content);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        p.add(scroll);
        return p;
    }

    public static JComponent scrollV(JComponent content) {
        JScrollPane scroll = new JScrollPane(content);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        return scroll;
    }

    public static JComponent scrollH(JComponent content) {
        JScrollPane scroll = new JScrollPane(content);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        return scroll;
    }

    public static JComponent scrollV(String title, JComponent content) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        JScrollPane scroll = new JScrollPane(content);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        p.add(scroll);
        return p;
    }

    public static class StringTransferable implements Transferable {
        private String string;

        private static final DataFlavor[] flavors = {DataFlavor.stringFlavor};

        public StringTransferable(String string) {
            this.string = string;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return flavors;
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return flavor.equals(flavors[0]);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
            return string;
        }
    }

    public static String nodeMappingToHTML(NodeMapping nodeMapping) {
        StringBuilder out = new StringBuilder("<html><table cellpadding='10'><tr><td>");
        out.append("<b>").append(nodeMapping.outputPath).append("</b>");
        out.append("<br/><br/>");
        for (Path path : nodeMapping.getInputPaths()) {
            out.append("&nbsp;&nbsp;&larr; ").append(path).append("<br/>");
        }
        out.append("</td></tr></table></html>");
        return out.toString();
    }

    public static String factEntryToHTML(Map.Entry<String, String> fact) {
        StringBuilder out = new StringBuilder("<table><tr><td>");
        out.append("<h3>").append(fact.getKey()).append("</h3>");
        out.append("</td></tr>");
        out.append("<tr><td>");
        out.append("<h4>").append(fact.getValue()).append("</h4>");
        out.append("</td></tr>");
        out.append("</table>");
        return out.toString();
    }

    public static String statsHTML(Path path, Stats.ValueStats valueStats, boolean details) {
        StringBuilder out = new StringBuilder("<html><table><tr><td>");
        out.append("<h3>").append(path.getTail()).append(" (").append(valueStats.total).append(")").append("</h3>");
        out.append("</td></tr>");
        out.append("<tr><td>");
        out.append(valueStats.getSummary());
        out.append("</td></tr>");
        if (details) {
            if (valueStats.values != null) {
                out.append("<tr><td><h4>Histogram</h4></td></tr><table>");
                for (Stats.Counter counter : valueStats.values.getDetails()) {
                    out.append("<tr><td>").append(counter).append("</td></tr>");
                }
                out.append("</table>");
            }
            if (valueStats.sample != null) {
                out.append("<tr><td><h4>Sample</h4></td></tr><table>");
                for (String value : valueStats.sample.getDetails()) {
                    out.append("<tr><td>").append(value).append("</td></tr>");
                }
                out.append("</table>");
            }
        }
        out.append("</table>");
        return out.toString();
    }

    public static String recDefNodeToHTML(RecDefNode node) {
        StringBuilder out = new StringBuilder("<html><table cellpadding='10'>");
        String what = node.isAttr() ? "Attribute" : "Element";
        out.append("<tr><td>").append(what).append(" ").append(node.getTag()).append("</td></tr>");
        for (NodeMapping nodeMapping : node.getNodeMappings().values()) {
            out.append("<tr><td><b>&larr; ").append(nodeMapping.inputPath).append("</b></td></tr>");
        }
        if (node.getDoc() != null) {
            if (node.getDoc().lines != null) {
                for (String line : node.getDoc().lines) {
                    out.append("<tr><td><p>").append(line).append("</p></td></tr>");
                }
            }
            if (node.getDoc().paras != null) {
                for (RecDef.DocParagraph para : node.getDoc().paras) {
                    out.append("<tr><td><b>").append(para.name).append("</b><br><p>")
                            .append(para.content).append("</p></td></tr>");
                }
            }
            if (node.getDoc().paraList != null) {
                for (RecDef.DocParagraph para : node.getDoc().paraList) {
                    out.append("<tr><td><b>").append(para.name).append("</b><br><p>")
                            .append(para.content).append("</p></td></tr>");
                }
            }
        }
        if (node.getOptList() != null) {
            out.append("<tr><td><ul>");
            for (OptList.Opt opt : node.getOptList().opts) {
                out.append("<li>").append(opt.value).append("</li>");
            }
            out.append("</ul></td></tr>");
        }
        return out.toString();
    }


    public static boolean launchURLFromXML(String urlFromXML) {
        return launchURL(urlFromXML.replaceAll("&amp;", "&"));
    }

    public static boolean launchURL(String urlString) {
        try {
            Desktop.getDesktop().browse(URI.create(urlString));
            return true;
        }
        catch (IOException e) {
            return false;
        }
    }

}
