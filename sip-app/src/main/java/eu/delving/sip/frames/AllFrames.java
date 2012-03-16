/*
 * Copyright 2011 DELVING BV
 *
 * Licensed under the EUPL, Version 1.0 or? as soon they
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

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import eu.delving.sip.base.Exec;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.model.SipModel;
import org.apache.commons.io.IOUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static eu.delving.sip.base.FrameBase.INSETS;

/**
 * Hold on to all the frames and manage their arrangemenbt
 *
 * @author Gerald de Jong
 */

public class AllFrames {
    private final String VIEW_PREF_PATTERN = "view_%s";
    private final String CURRENT_VIEW_PREF = "currentView";
    private Dimension LARGE_ICON_SIZE = new Dimension(80, 50);
    private Dimension SMALL_ICON_SIZE = new Dimension(30, 18);
    private FrameBase[] frames;
    private List<Arrangement> arrangements = new ArrayList<Arrangement>();
    private JDesktopPane desktop;
    private String currentView = "";
    private SipModel sipModel;

    private void addSpaceBarCreate(CreateFrame create, FrameBase analysis) {
        final String CREATE = "create";
        analysis.getActionMap().put(CREATE, create.getAction());
        analysis.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(' '), CREATE);
    }

    public AllFrames(JDesktopPane desktop, final SipModel sipModel) {
        this.desktop = desktop;
        this.sipModel = sipModel;
        CreateFrame create = new CreateFrame(desktop, sipModel);
        addSpaceBarCreate(create, create);
        StatisticsFrame statistics = new StatisticsFrame(desktop, sipModel);
        FrameBase analysis = new AnalysisFrame(desktop, sipModel, statistics);
        addSpaceBarCreate(create, analysis);
        RecDefFrame recDef = new RecDefFrame(desktop, sipModel);
        addSpaceBarCreate(create, recDef);
        FrameBase input = new InputFrame(desktop, sipModel);
        FrameBase recMapping = new RecMappingFrame(desktop, sipModel);
        FrameBase fieldMapping = new FieldMappingFrame(desktop, sipModel);
        FrameBase output = new OutputFrame(desktop, sipModel);
        this.frames = new FrameBase[]{
                analysis,
                create,
                recDef,
                statistics,
                input,
                recMapping,
                fieldMapping,
                output
        };
        try {
            File file = sipModel.getStorage().getFrameArrangementFile();
            if (!file.exists()) {
                List<String> lines = IOUtils.readLines(getClass().getResource("/frame-arrangements.xml").openStream());
                IOUtils.writeLines(lines, "\n", new FileOutputStream(file));
            }
            addFrameArrangements(new FileInputStream(file));
        }
        catch (IOException e) {
            throw new RuntimeException("Initializing views", e);
        }
    }

    private void addFrameArrangements(InputStream inputStream) {
        XStream stream = new XStream();
        stream.processAnnotations(FrameArrangements.class);
        FrameArrangements views = (FrameArrangements) stream.fromXML(inputStream);
        int viewIndex = 0;
        for (XArrangement view : views.arrangements) {
            Arrangement arrangement = new Arrangement(view, viewIndex++);
            for (XFrame frame : view.frames) {
                arrangement.blocks.add(block(frame.getX(), frame.getY(), frame.getW(), frame.getH(), frame.which));
            }
            arrangements.add(arrangement);
        }
    }

    public Runnable prepareForNothing() {
        return new Runnable() {
            @Override
            public void run() {
                selectView("");
            }
        };
    }

    public Runnable prepareForMapping(final JComponent component) {
        return new Runnable() {
            @Override
            public void run() {
                selectView(component.getSize().width > 1600 ? "Decadent Display" : "Quick Mapping");
            }
        };
    }

    public Runnable prepareForInvestigation(final JComponent component) {
        return new Runnable() {
            @Override
            public void run() {
                selectView(component.getSize().width > 1600 ? "Decadent Display" : "Big Picture");
            }
        };
    }

    public Runnable prepareForDelimiting() {
        return new Runnable() {
            @Override
            public void run() {
                selectView("First Contact");
            }
        };
    }

    public void restore() {
        Exec.swingLater(new Runnable() {
            @Override
            public void run() {
                selectView(sipModel.getPreferences().get(CURRENT_VIEW_PREF, ""));
            }
        });
    }

    public JMenu getFrameMenu() {
        JMenu menu = new JMenu("Frames");
        int index = 1;
        for (FrameBase frame : frames) {
            frame.setAccelerator(index++);
            menu.add(frame.getAction());
        }
        return menu;
    }

    public JMenu getViewMenu() {
        JMenu menu = new JMenu("View");
        for (Action action : arrangements) {
            menu.add(action);
        }
        return menu;
    }

    public JPanel getButtonPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createTitledBorder("Views"));
        for (Arrangement a : arrangements) {
            JButton b = new JButton(a);
            b.setHorizontalTextPosition(JButton.CENTER);
            b.setVerticalTextPosition(JButton.BOTTOM);
            b.setFont(new Font("Sans", Font.ITALIC, 10));
            p.add(b);
            p.add(Box.createVerticalStrut(5));
        }
        p.add(Box.createVerticalGlue());
        return p;
    }

    private FrameBase frame(FrameBase.Which which) {
        for (FrameBase frame : frames) if (frame.getWhich() == which) return frame;
        throw new RuntimeException(which + " not found");
    }

    private void selectView(String viewName) {
        if (currentView.equals(viewName)) return;
        for (Arrangement arrangement : arrangements) {
            if (arrangement.toString().equals(viewName)) {
                arrangement.actionPerformed(null);
                sipModel.getFeedback().say(String.format("Selecting view %s", arrangement));
                return;
            }
        }
        for (FrameBase frame : frames) frame.closeFrame();
        currentView = "";
    }

    private Block block(int x, int y, int w, int h, FrameBase.Which which) {
        return new Block(frame(which), x, y, w, h);
    }

    private static class Situation implements FrameBase.Placement {
        private Point location;
        private Dimension size;

        private Situation(Point location, Dimension size) {
            this.location = location;
            this.size = size;
        }

        public Point getLocation() {
            return location;
        }

        public Dimension getSize() {
            return size;
        }
    }

    private static class Block {
        FrameBase frame;
        int x, y, w, h;

        private Block(FrameBase frame, int x, int y, int w, int h) {
            this.frame = frame;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        Situation situate(Dimension all, int rows, int cols, boolean useInsets) {
            int wx = all.width / cols - (all.width % 2);
            int hx = all.height / rows - (all.height % 2);
            if (useInsets) {
                return new Situation(
                        new Point(x * wx - INSETS.left, y * hx - INSETS.top),
                        new Dimension(w * wx + INSETS.left + INSETS.right, h * hx + INSETS.top + INSETS.bottom)
                );
            }
            else {
                return new Situation(
                        new Point(x * wx, y * hx),
                        new Dimension(w * wx, h * hx)
                );
            }
        }

        int rows(int max) {
            return Math.max(max, y + h);
        }

        int cols(int max) {
            return Math.max(max, x + w);
        }
    }

    private class Arrangement extends AbstractAction {
        List<Block> blocks = new ArrayList<Block>();

        Arrangement(XArrangement view, int viewIndex) {
            super(view.name);
            putValue(
                    Action.ACCELERATOR_KEY,
                    KeyStroke.getKeyStroke(KeyEvent.VK_0 + viewIndex, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask())
            );
            this.blocks = blocks;
            putValue(Action.SMALL_ICON, new ViewIcon(this, SMALL_ICON_SIZE));
            putValue(Action.LARGE_ICON_KEY, new ViewIcon(this, LARGE_ICON_SIZE));
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            int rows = 0;
            int cols = 0;
            for (Block block : blocks) {
                rows = block.rows(rows);
                cols = block.cols(cols);
            }
            for (FrameBase frame : frames) {
                frame.closeFrame();
            }
            for (Block block : blocks) {
                Situation situation = block.situate(desktop.getSize(), rows, cols, true);
                block.frame.setPlacement(situation);
            }
            for (Block block : blocks) {
                block.frame.openFrame();
            }
            sipModel.getPreferences().put(CURRENT_VIEW_PREF, currentView = toString());
        }

        public String toString() {
            return (String) this.getValue(Action.NAME);
        }
    }

    private static class ViewIcon implements Icon {
        private Arrangement a;
        private Dimension size;

        private ViewIcon(Arrangement a, Dimension size) {
            this.a = a;
            this.size = size;
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics;
            int rows = 0;
            int cols = 0;
            for (Block block : a.blocks) {
                rows = block.rows(rows);
                cols = block.cols(cols);
            }
            for (Block block : a.blocks) {
                Situation situation = block.situate(size, rows, cols, false);
                g.drawRect(x + situation.getLocation().x + 1, y + situation.getLocation().y + 1, situation.getSize().width, situation.getSize().height);
            }
        }

        @Override
        public int getIconWidth() {
            return size.width + 4;
        }

        @Override
        public int getIconHeight() {
            return size.height + 4;
        }
    }

    @XStreamAlias("frame-arrangements")
    public static class FrameArrangements {
        @XStreamImplicit
        List<XArrangement> arrangements;
    }

    @XStreamAlias("arrangement")
    public static class XArrangement {
        @XStreamAsAttribute
        String name;

        @XStreamImplicit
        List<XFrame> frames;
    }

    @XStreamAlias("frame")
    public static class XFrame {
        @XStreamAsAttribute
        String where;

        @XStreamAsAttribute
        FrameBase.Which which;

        public int getX() {
            return locVal(0);
        }

        public int getY() {
            return locVal(1);
        }

        public int getW() {
            return locVal(2);
        }

        public int getH() {
            return locVal(3);
        }

        private int locVal(int index) {
            return where.charAt(index) - '0';
        }
    }
}
