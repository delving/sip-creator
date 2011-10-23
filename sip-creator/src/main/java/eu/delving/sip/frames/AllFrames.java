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

import eu.delving.sip.base.FrameBase;
import eu.delving.sip.menus.EditHistory;
import eu.delving.sip.model.SipModel;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JMenu;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import static eu.delving.sip.base.FrameBase.INSETS;

/**
 * Hold on to all the frames and manage their arrangemenbt
 *
 * @author Gerald de Jong
 */

public class AllFrames {
    private Dimension ICON_SIZE = new Dimension(80, 50);
    private FrameBase[] frames;
    private Arrangement[] views;
    private JDesktopPane desktop;
    private int viewIndex = 1;

    public AllFrames(JDesktopPane desktop, SipModel sipModel, EditHistory editHistory) {
        this.desktop = desktop;
        FrameBase status, analysis, create, statistics, input, fieldMapping, recordMapping, output;
        this.frames = new FrameBase[]{
                status = new FactsFrame(desktop, sipModel),
                analysis = new AnalysisFrame(desktop, sipModel),
                create = new CreateFrame(desktop, sipModel),
                statistics = new StatisticsFrame(desktop, sipModel),
                input = new InputFrame(desktop, sipModel),
                fieldMapping = new FieldMappingFrame(desktop, sipModel, editHistory),
                recordMapping = new RecordMappingFrame(desktop, sipModel),
                output = new OutputFrame(desktop, sipModel)
        };
        this.views = new Arrangement[]{
                view("First contact",
                        block(analysis, 0, 0, 1, 3),
                        block(statistics, 1, 0, 1, 2),
                        block(status, 1, 2)
                ),
                view("Quick mapping",
                        block(create, 0, 0),
                        block(statistics, 1, 0),
                        block(recordMapping, 2, 0)
                ),
                view("Code tweaking",
                        block(recordMapping, 0, 0),
                        block(fieldMapping, 1, 0, 3, 1),
                        block(input, 4, 0)
                ),
                view("Deep delving",
                        block(fieldMapping, 0, 0)
                ),
                view("Big picture",
                        block(input, 0, 0),
                        block(recordMapping, 1, 0),
                        block(output, 2, 0)
                ),
                view("Show and tell",
                        block(analysis, 0, 0),
                        block(create, 0, 0),
                        block(statistics, 0, 0),
                        block(input, 0, 0),
                        block(recordMapping, 0, 0),
                        block(fieldMapping, 0, 0),
                        block(output, 0, 0),
                        block(status, 0, 0)
                ),
                view("Decadent Display",
                        block(create, 0, 0, 3, 3),
                        block(statistics, 0, 3, 2, 3),
                        block(input, 5, 0, 2, 4),
                        block(recordMapping, 3, 0, 2, 3),
                        block(fieldMapping, 2, 3, 3, 5),
                        block(output, 5, 4, 2, 4),
                        block(status, 0, 6, 2, 2)
                )
        };
    }

    public void putState() {
        for (FrameBase frame : frames) frame.putState();
    }

    public void restore() {
        for (FrameBase frame : frames) if (frame.wasVisible()) frame.openFrame(true);
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
        for (Action action : views) {
            menu.add(action);
        }
        return menu;
    }

    public JPanel getButtonPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createTitledBorder("Views"));
        for (Arrangement a : views) {
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

    private Arrangement view(String name, Block... blocks) {
        return new Arrangement(name, viewIndex++, blocks);
    }

    private Block block(FrameBase frame, int x, int y) {
        return block(frame, x, y, 1, 1);
    }

    private Block block(FrameBase frame, int x, int y, int w, int h) {
        return new Block(frame, x, y, w, h);
    }

    private class Situation {
        Point location;
        Dimension size;
    }

    private class Block {
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
            Situation situation = new Situation();
            situation.location = useInsets ? new Point(x * wx - INSETS.left, y * hx - INSETS.top) : new Point(x * wx, y * hx);
            situation.size = useInsets ? new Dimension(w * wx + INSETS.left + INSETS.right, h * hx + INSETS.top + INSETS.bottom) : new Dimension(w * wx, h * hx);
            return situation;
        }

        int rows(int max) {
            return Math.max(max, y + h);
        }

        int cols(int max) {
            return Math.max(max, x + w);
        }
    }

    private class Arrangement extends AbstractAction {
        Block[] blocks;

        Arrangement(String name, int viewIndex, Block[] blocks) {
            super(name);
            putValue(
                    Action.ACCELERATOR_KEY,
                    KeyStroke.getKeyStroke(KeyEvent.VK_0 + viewIndex, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask())
            );
            this.blocks = blocks;
            putValue(Action.SMALL_ICON, new ViewIcon(this));
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
                block.frame.setLocation(situation.location);
                block.frame.setSize(situation.size);
            }
            for (Block block : blocks) {
                block.frame.openFrame(false);
            }
        }

    }

    private class ViewIcon implements Icon {

        private Arrangement a;

        private ViewIcon(Arrangement a) {
            this.a = a;
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
                Situation situation = block.situate(ICON_SIZE, rows, cols, false);
                g.drawRect(x + situation.location.x + 1, y + situation.location.y + 1, situation.size.width, situation.size.height);
            }
        }

        @Override
        public int getIconWidth() {
            return ICON_SIZE.width + 4;
        }

        @Override
        public int getIconHeight() {
            return ICON_SIZE.height + 4;
        }
    }

}
