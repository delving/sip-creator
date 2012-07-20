/*
 * Copyright 2011, 2012 Delving BV
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
import eu.delving.sip.base.CultureHubClient;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.Storage;
import eu.delving.sip.model.SipModel;
import org.apache.commons.io.IOUtils;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static eu.delving.sip.base.FrameBase.INSETS;

/**
 * Hold on to all the frames and manage their arrangement on the desktop.  It is possible to make adjustments in
 * how the frames are arranged in the different views because the default arrangements are copied into a file the
 * data directory and can be modified for subsequent runs of the program.  Useful changes should be copied
 * back into the default XML file eventually.
 *
 * @author Gerald de Jong
 */

public class AllFrames {
    private final String CURRENT_VIEW_PREF = "currentView";
    private Dimension LARGE_ICON_SIZE = new Dimension(80, 50);
    private Dimension SMALL_ICON_SIZE = new Dimension(30, 18);
    private FrameBase[] frames;
    private FunctionFrame functionFrame;
    private MappingCodeFrame mappingCodeFrame;
    private StatsFrame statsFrame;
    private WorkFrame workFrame;
    private UploadFrame uploadFrame;
    private FrameArrangements frameArrangements;
    private List<Arrangement> arrangements = new ArrayList<Arrangement>();
    private JDesktopPane desktop;
    private String currentView = "";
    private SipModel sipModel;

    private void addSpaceBarCreate(CreateFrame create, FrameBase analysis) {
        final String CREATE = "create";
        analysis.getActionMap().put(CREATE, create.getCreateMappingAction());
        analysis.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(' '), CREATE);
    }

    public AllFrames(JDesktopPane desktop, final SipModel sipModel, final CultureHubClient cultureHubClient) {
        this.desktop = desktop;
        this.sipModel = sipModel;
        functionFrame = new FunctionFrame(desktop, sipModel);
        mappingCodeFrame = new MappingCodeFrame(desktop, sipModel);
        statsFrame = new StatsFrame(desktop, sipModel);
        workFrame = new WorkFrame(desktop, sipModel);
        uploadFrame = new UploadFrame(desktop, sipModel, cultureHubClient);
        CreateFrame create = new CreateFrame(desktop, sipModel);
        addSpaceBarCreate(create, create);
        FrameBase source = new SourceFrame(desktop, sipModel);
        addSpaceBarCreate(create, source);
        TargetFrame target = new TargetFrame(desktop, sipModel);
        addSpaceBarCreate(create, target);
        FrameBase input = new InputFrame(desktop, sipModel);
        FrameBase recMapping = new RecMappingFrame(desktop, sipModel);
        FrameBase fieldMapping = new FieldMappingFrame(desktop, sipModel);
        FrameBase output = new OutputFrame(desktop, sipModel);
        this.frames = new FrameBase[]{
                source,
                create,
                target,
                input,
                recMapping,
                fieldMapping,
                output
        };
        try {
            File file = frameArrangementsFile();
            createDefaultFrameArrangements(file);
            try {
                addFrameArrangements(new FileInputStream(file));
            }
            catch (Exception e) {
                createDefaultFrameArrangements(file);
                addFrameArrangements(new FileInputStream(file));
            }
        }
        catch (IOException e) {
            throw new RuntimeException("Initializing views", e);
        }
    }

    private File frameArrangementsFile() {
        return this.sipModel.getStorage().cache(Storage.FRAME_ARRANGEMENTS_FILE);
    }

    private void createDefaultFrameArrangements(File file) throws IOException {
        List<String> lines = IOUtils.readLines(getClass().getResource("/frame-arrangements.xml").openStream());
        IOUtils.writeLines(lines, "\n", new FileOutputStream(file));
    }

    private void addFrameArrangements(InputStream inputStream) {
        XStream stream = new XStream();
        stream.processAnnotations(FrameArrangements.class);
        frameArrangements = (FrameArrangements) stream.fromXML(inputStream);
        int viewIndex = 1;
        for (XArrangement view : frameArrangements.arrangements) {
            Arrangement arrangement = new Arrangement(view, viewIndex++);
            for (XFrame frame : view.frames) arrangement.blocks.add(new Block(frame));
            arrangements.add(arrangement);
            arrangement.source = view;
        }
    }

    public Swing prepareForNothing() {
        return new Swing() {
            @Override
            public void run() {
                selectNewView("");
            }
        };
    }

    public Swing prepareForMapping(final JComponent component) {
        return new Swing() {
            @Override
            public void run() {
                selectNewView(component.getSize().width > 1600 ? "Decadent Display" : "Quick Mapping");
            }
        };
    }

    public Swing prepareForInvestigation(final JComponent component) {
        return new Swing() {
            @Override
            public void run() {
                selectNewView(component.getSize().width > 1600 ? "Decadent Display" : "Big Picture");
            }
        };
    }

    public Swing prepareForDelimiting() {
        return new Swing() {
            @Override
            public void run() {
                selectNewView("First Contact");
            }
        };
    }

    public Action getUploadAction() {
        return uploadFrame.getUploadAction();
    }

    public void restore() {
        selectNewView(sipModel.getPreferences().get(CURRENT_VIEW_PREF, ""));
    }

    public JMenu getFrameMenu() {
        JMenu menu = new JMenu("Frames");
        int index = 1;
        for (FrameBase frame : frames) {
            frame.setAccelerator(index++);
            menu.add(frame.getAction());
        }
        menu.addSeparator();
        menu.add(statsFrame.getAction());
        menu.add(functionFrame.getAction());
        menu.add(mappingCodeFrame.getAction());
        menu.add(workFrame.getAction());
        menu.add(uploadFrame.getAction());
        return menu;
    }

    public JMenu getViewMenu() {
        JMenu menu = new JMenu("View");
        for (Action action : arrangements) menu.add(action);
        menu.addSeparator();
        menu.add(new EditAction());
        return menu;
    }

    public JPanel getSidePanel() {
        JPanel arrangements = new JPanel();
        arrangements.setLayout(new BoxLayout(arrangements, BoxLayout.Y_AXIS));
        arrangements.setBorder(BorderFactory.createTitledBorder("Arrangements"));
        for (Arrangement a : this.arrangements) {
            JButton b = new JButton(a);
            b.setHorizontalTextPosition(JButton.CENTER);
            b.setVerticalTextPosition(JButton.BOTTOM);
            b.setFont(new Font("Sans", Font.ITALIC, 10));
            arrangements.add(b);
            arrangements.add(Box.createVerticalStrut(5));
        }
        arrangements.add(Box.createVerticalGlue());
        JPanel work = new JPanel(new BorderLayout());
        work.add(miniScrollV("Work", workFrame.getMiniList()));
        JPanel p = new JPanel(new BorderLayout());
        p.add(arrangements, BorderLayout.CENTER);
        p.add(work, BorderLayout.SOUTH);
        p.setPreferredSize(new Dimension(110, 400));
        workFrame.getMiniList().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting() || workFrame.getMiniList().isSelectionEmpty()) return;
                workFrame.getAction().actionPerformed(null);
            }
        });
        return p;
    }

    public static JComponent miniScrollV(String title, JComponent content) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        JScrollPane scroll = new JScrollPane(content);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        p.add(scroll);
        return p;
    }


    public JPanel getBigWindowsPanel() {
        JPanel p = new JPanel(new GridLayout(1, 0));
        p.setBorder(BorderFactory.createTitledBorder("Global Frames"));
        p.add(createHotkeyButton(statsFrame));
        p.add(createHotkeyButton(functionFrame));
        p.add(createHotkeyButton(mappingCodeFrame));
        return p;
    }

    private JButton createHotkeyButton(FrameBase frame) {
        Action action = frame.getAction();
        JButton button = new JButton(action);
        KeyStroke stroke = (KeyStroke) action.getValue(Action.ACCELERATOR_KEY);
        if (stroke != null) {
            String modifiers = KeyEvent.getKeyModifiersText(stroke.getModifiers());
            String keyText = KeyEvent.getKeyText(stroke.getKeyCode());
            button.setText(button.getText() + " " + modifiers + keyText);
        }
        return button;
    }

    public void rebuildView() {
        if (currentView.isEmpty()) return;
        selectView(currentView);
    }

    private FrameBase frame(FrameBase.Which which) {
        for (FrameBase frame : frames) if (frame.getWhich() == which) return frame;
        throw new RuntimeException(which + " not found");
    }

    private void selectNewView(String viewName) {
        if (currentView.equals(viewName)) return;
        selectView(viewName);
    }

    private void selectView(String viewName) {
        for (Arrangement arrangement : arrangements) {
            if (arrangement.toString().equals(viewName)) {
                arrangement.actionPerformed(null);
                return;
            }
        }
        for (FrameBase frame : frames) frame.closeFrame();
        currentView = "";
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

    private class Block {
        XFrame original;
        FrameBase frame;

        private Block(XFrame frame) {
            this.original = frame;
            this.frame = frame(frame.which);
        }

        Situation situate(Dimension all, int rows, int cols, boolean useInsets) {
            int wx = all.width / cols - (all.width % 2);
            int hx = all.height / rows - (all.height % 2);
            if (useInsets) {
                return new Situation(
                        new Point(original.getX() * wx - INSETS.left, original.getY() * hx - INSETS.top),
                        new Dimension(original.getW() * wx + INSETS.left + INSETS.right, original.getH() * hx + INSETS.top + INSETS.bottom)
                );
            }
            else {
                return new Situation(
                        new Point(original.getX() * wx, original.getY() * hx),
                        new Dimension(original.getW() * wx, original.getH() * hx)
                );
            }
        }

        int rows(int max) {
            return Math.max(max, original.getY() + original.getH());
        }

        int cols(int max) {
            return Math.max(max, original.getX() + original.getW());
        }
    }

    private class Arrangement extends AbstractAction implements Swing {
        List<Block> blocks = new ArrayList<Block>();
        public XArrangement source;
        private ViewIcon small, large;

        Arrangement(XArrangement source, int viewIndex) {
            super(source.name);
            putValue(
                    Action.ACCELERATOR_KEY,
                    KeyStroke.getKeyStroke(KeyEvent.VK_0 + viewIndex, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask())
            );
            small = new ViewIcon(this, SMALL_ICON_SIZE);
            putValue(Action.SMALL_ICON, small);
            large = new ViewIcon(this, LARGE_ICON_SIZE);
            putValue(Action.LARGE_ICON_KEY, large);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            int rows = 0;
            int cols = 0;
            for (Block block : blocks) {
                rows = block.rows(rows);
                cols = block.cols(cols);
            }
            for (FrameBase frame : frames) frame.closeFrame();
            for (Block block : blocks) {
                Situation situation = block.situate(desktop.getSize(), rows, cols, true);
                block.frame.setPlacement(situation);
            }
            for (Block block : blocks) block.frame.openFrame();
            sipModel.getPreferences().put(CURRENT_VIEW_PREF, currentView = toString());
            for (FrameBase base : frames) base.setArrangementSource(source, this);
        }

        public String toString() {
            return (String) this.getValue(Action.NAME);
        }

        @Override
        public void run() {
            actionPerformed(null);
            small.refresh();
            large.refresh();
            sipModel.exec(new Work() {
                @Override
                public void run() {
                    XStream stream = new XStream();
                    stream.processAnnotations(FrameArrangements.class);
                    try {
                        stream.toXML(frameArrangements, new FileOutputStream(frameArrangementsFile()));
                    }
                    catch (FileNotFoundException e) {
                        // eat it.
                    }
                }

                @Override
                public Job getJob() {
                    return Job.READ_FRAME_ARRANGEMENTS;
                }
            });
        }
    }

    private class EditAction extends AbstractAction {
        public EditAction() {
            super("Edit Frame Arrangements");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            for (FrameBase frame : frames) frame.toggleEditMenu();
        }
    }

    private static class ViewIcon implements Icon {
        private Arrangement a;
        private Dimension size;
        private Component component;

        private ViewIcon(Arrangement a, Dimension size) {
            this.a = a;
            this.size = size;
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            this.component = component;
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

        public void refresh() {
            component.repaint();
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
        public String name;

        @XStreamImplicit
        public List<XFrame> frames;
    }

    @XStreamAlias("frame")
    public static class XFrame {
        @XStreamAsAttribute
        public String where;

        @XStreamAsAttribute
        public FrameBase.Which which;

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
