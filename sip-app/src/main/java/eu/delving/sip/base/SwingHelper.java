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
import eu.delving.stats.Stats;
import org.apache.log4j.Logger;

import javax.jnlp.BasicService;
import javax.jnlp.ServiceManager;
import javax.jnlp.UnavailableServiceException;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

/**
 * Gathering together a number of things that are done with the Swing library from many parts of the code.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class SwingHelper {
    private static Color NORMAL_BG = Color.WHITE;
    private static Color NORMAL_FG = Color.BLACK;
    private static Color ERROR_BG = new Color(255, 200, 200);
    public static Color UNEDITABLE_BG = new Color(255, 255, 200);
    private static Color DELIMITER_BG = new Color(255, 255, 200);
    public static Color MAPPED_COLOR = new Color(220, 255, 220);
    public static Color HILIGHTED_COLOR = new Color(255, 205, 205);
    public static Color LONG_TERM_JOB_COLOR = new Color(255, 180, 180);
    public static Color NORMAL_JOB_COLOR = new Color(200, 255, 200);
    public static Color REPORT_OK = new Color(0, 50, 0);
    public static Color REPORT_ERROR = new Color(50, 0, 0);

    public static final Icon ICON_VALUE = icon("value");
    public static final Icon ICON_COMPOSITE = icon("composite");
    public static final Icon ICON_ATTRIBUTE = icon("attribute");
    public static final Icon ICON_UNMAPPABLE = icon("unmappable");
    public static final Icon ICON_REWIND = icon("rewind");
    public static final Icon ICON_PLAY = icon("play");
    public static final Icon ICON_VALIDATE = icon("validate");
    public static final Icon ICON_UNLOCK = icon("unlock");
    public static final Icon ICON_SELECT_ANOTHER = icon("choose-another");
    public static final Icon ICON_DOWNLOAD = icon("download");
    public static final Icon ICON_UPLOAD = icon("upload");
    public static final Icon ICON_IMPORT = icon("import");
    public static final Icon ICON_EMPTY = icon("empty");
    public static final Icon ICON_OWNED = icon("owned");
    public static final Icon ICON_UNAVAILABLE = icon("unavailable");
    public static final Icon ICON_BUSY = icon("busy");
    public static final Icon ICON_HUH = icon("huh");
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
        component.setBackground(editable ? NORMAL_BG : UNEDITABLE_BG);
    }

    public static void setError(JTextComponent component, boolean error) {
        component.setBackground(error ? ERROR_BG : NORMAL_BG);
    }

    public static void setDelimitedColor(JComponent component, boolean selected) {
        component.setBackground(selected ? NORMAL_BG : DELIMITER_BG);
        component.setForeground(selected ? DELIMITER_BG : NORMAL_FG);
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

    public static boolean isDevelopmentMode() {
        try {
            ServiceManager.lookup("javax.jnlp.BasicService");
            return false;
        }
        catch (UnavailableServiceException ue) {
            return true;
        }
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
        try {
            return launchURL(new URL(urlFromXML.replaceAll("&amp;", "&")));
        }
        catch (MalformedURLException e1) {
            return false;
        }
    }

    public static boolean launchURL(URL url) {
        try {
            BasicService bs = (BasicService) ServiceManager.lookup("javax.jnlp.BasicService");
            return bs.showDocument(url);
        }
        catch (UnavailableServiceException ue) {
            Logger.getLogger(SwingHelper.class).info("Launch " + url);
            return false;
        }
    }

}
