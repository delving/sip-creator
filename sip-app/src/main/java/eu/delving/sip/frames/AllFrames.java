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

package eu.delving.sip.frames;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import eu.delving.XStreamFactory;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.Storage;
import eu.delving.sip.model.SipModel;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.WordUtils;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static eu.delving.sip.base.FrameBase.INSETS;
import static eu.delving.sip.base.KeystrokeHelper.SPACE;
import static eu.delving.sip.base.KeystrokeHelper.addKeyboardAction;
import static eu.delving.sip.base.KeystrokeHelper.menuDigit;
import static eu.delving.sip.base.SwingHelper.scrollV;
import static eu.delving.sip.frames.AllFrames.View.BIG_PICTURE;
import static eu.delving.sip.frames.AllFrames.View.CLEAR;
import static eu.delving.sip.frames.AllFrames.View.DATA_SETS;
import static eu.delving.sip.frames.AllFrames.View.DECADENT_DISPLAY;
import static eu.delving.sip.frames.AllFrames.View.QUICK_MAPPING;

/**
 * Hold on to all the frames and manage their arrangement on the desktop.  It is possible to make adjustments in
 * how the frames are arranged in the different views because the default arrangements are copied into a file the
 * data directory and can be modified for subsequent runs of the program.  Useful changes should be copied
 * back into the default XML file eventually.
 *
 */

public class AllFrames {
    private Dimension LARGE_ICON_SIZE = new Dimension(80, 50);
    private JPanel content;
    private FrameBase[] frames;
    private FrameBase dataSetFrame;
    private FrameArrangements frameArrangements;
    private List<Arrangement> arrangements = new ArrayList<Arrangement>();
    private View currentView = CLEAR;
    private SipModel sipModel;
    private ViewSelector viewSelector = new ViewSelector() {
        @Override
        public void selectView(View view) {
            for (Arrangement arrangement : arrangements) {
                if (arrangement.source.view == view) {
                    arrangement.actionPerformed(null);
                    currentView = arrangement.source.view;
                    return;
                }
            }
            for (FrameBase frame : frames) frame.closeFrame();
            currentView = CLEAR;
        }

        @Override
        public void refreshView() {
            selectView(currentView);
        }
    };

    public enum View {
        CLEAR,
        DATA_SETS,
        QUICK_MAPPING,
        BIG_PICTURE,
        CODE_TWEAKING,
        DEEP_DELVING,
        DECADENT_DISPLAY,
        FUNCTIONS,
        STATISTICS,
        REPORT,
        MAPPING_CODE,
        LOG;

        public String getHtml() {
            return WordUtils.capitalizeFully(super.toString().replaceAll("_", " "));
        }
    }

    public interface ViewSelector {
        void selectView(View view);

        void refreshView();
    }

    public AllFrames(final SipModel sipModel, JPanel content, FrameBase dataSetFrame, FrameBase logFrame) {
        this.sipModel = sipModel;
        this.content = content;
        this.dataSetFrame = dataSetFrame;
        sipModel.setViewSelector(viewSelector);
        FunctionFrame functionFrame = new FunctionFrame(sipModel);
        MappingCodeFrame mappingCodeFrame = new MappingCodeFrame(sipModel);
        StatsFrame statsFrame = new StatsFrame(sipModel);
        CreateFrame create = new CreateFrame(sipModel);
        Action createAction = create.getCreateMappingAction();
        addKeyboardAction(createAction, SPACE, create);
        FrameBase source = new SourceFrame(sipModel);
        addKeyboardAction(createAction, SPACE, source);
        TargetFrame target = new TargetFrame(sipModel);
        addKeyboardAction(createAction, SPACE, target);
        FrameBase input = new InputFrame(sipModel);
        FrameBase recMapping = new RecMappingFrame(sipModel);
        FrameBase fieldMapping = new FieldMappingFrame(sipModel);
        FrameBase output = new OutputFrame(sipModel);
        FrameBase reportFrame = new ReportFrame(sipModel);
        this.frames = new FrameBase[]{
                dataSetFrame,
                source,
                create,
                target,
                input,
                recMapping,
                fieldMapping,
                output,
                functionFrame,
                statsFrame,
                reportFrame,
                mappingCodeFrame,
                logFrame
        };
        try {
            File file = frameArrangementsFile();
            createDefaultFrameArrangements(file);
            try {
                addFrameArrangements(new FileInputStream(file));
            }
            catch (Exception e) {
                // when you want to adjust, remove the line above and put it here: createDefaultFrameArrangements(file);
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
        frameArrangements = (FrameArrangements) XStreamFactory.getStreamFor(FrameArrangements.class).fromXML(inputStream);
        int viewIndex = 0;
        for (XArrangement view : frameArrangements.arrangements) {
            Arrangement arrangement = new Arrangement(view, viewIndex++);
            for (XFrame frame : view.frames) arrangement.blocks.add(new Block(frame));
            arrangements.add(arrangement);
            arrangement.source = view;
        }
    }

    public FrameBase[] getFrames() {
        return frames;
    }

    public ViewSelector getViewSelector() {
        return viewSelector;
    }

    public Swing prepareForNothing() {
        return new Swing() {
            @Override
            public void run() {
                viewSelector.selectView(CLEAR);
            }
        };
    }

    public Swing prepareForMapping(final JComponent component) {
        return new Swing() {
            @Override
            public void run() {
                viewSelector.selectView(component.getSize().width > 1600 ? DECADENT_DISPLAY : QUICK_MAPPING);
            }
        };
    }

    public Swing prepareForInvestigation(final JComponent component) {
        return new Swing() {
            @Override
            public void run() {
                viewSelector.selectView(component.getSize().width > 1600 ? DECADENT_DISPLAY : BIG_PICTURE);
            }
        };
    }

    public void initiate() {
        sipModel.getDataSetModel().clearDataSet();
        sipModel.exec(new Swing() {
            @Override
            public void run() {
                viewSelector.selectView(DATA_SETS);
                dataSetFrame.refresh();
            }
        });
    }

    public JMenu getViewMenu() {
        JMenu menu = new JMenu("View");
        for (Action action : arrangements) menu.add(action);
        return menu;
    }

    public JComponent getSidePanel() {
        JPanel arrangements = new JPanel();
        arrangements.setLayout(new BoxLayout(arrangements, BoxLayout.Y_AXIS));
        for (Arrangement a : this.arrangements) {
            JButton b = new JButton(a);
            b.setHorizontalTextPosition(JButton.CENTER);
            b.setVerticalTextPosition(JButton.BOTTOM);
            b.setFont(new Font("Sans", Font.PLAIN, 10));
            arrangements.add(b);
            arrangements.add(Box.createVerticalStrut(5));
        }
        arrangements.add(Box.createVerticalGlue());
        JPanel p = new JPanel(new BorderLayout());
        p.add(arrangements, BorderLayout.CENTER);
        //return scrollV(p);
        return new JScrollPane(arrangements, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    }

    public static JComponent miniScrollV(String title, JComponent content) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        JScrollPane scroll = new JScrollPane(content);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        scroll.setPreferredSize(new Dimension(200, 80));
        p.add(scroll);
        return p;
    }

    private FrameBase frame(FrameBase.Which which) {
        for (FrameBase frame : frames) if (frame.getWhich() == which) return frame;
        throw new RuntimeException(which + " not found");
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
            int wx = (all.width - (useInsets ? INSETS.right : all.width % 2)) / cols;
            int hx = (all.height - (useInsets ? INSETS.bottom : all.height % 2)) / rows;
            if (useInsets) {
                return new Situation(
                        new Point(original.getX() * wx + INSETS.left, original.getY() * hx + INSETS.top),
                        new Dimension(original.getW() * wx - INSETS.left, original.getH() * hx - INSETS.top)
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
        private ViewIcon icon;

        Arrangement(XArrangement source, int viewIndex) {
            super(source.view.getHtml());
            if (viewIndex < 9) {
                putValue(ACCELERATOR_KEY, menuDigit(viewIndex + 1));
            }
            icon = new ViewIcon(this, LARGE_ICON_SIZE);
            putValue(Action.LARGE_ICON_KEY, icon);
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
                Situation situation = block.situate(sipModel.getDesktop().getSize(), rows, cols, true);
                block.frame.setPlacement(situation);
            }
            for (Block block : blocks) block.frame.openFrame();
            for (FrameBase base : frames) base.setArrangementSource(source, this);
            currentView = source.view;
            content.requestFocus();
        }

        public String toString() {
            return (String) this.getValue(Action.NAME);
        }

        @Override
        public void run() {
            actionPerformed(null);
            sipModel.exec(new Work() {
                @Override
                public void run() {
                    try {
                        XStreamFactory.getStreamFor(FrameArrangements.class).toXML(frameArrangements, new FileOutputStream(frameArrangementsFile()));
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
            g.setColor(Color.GRAY);
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
        public View view;

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
