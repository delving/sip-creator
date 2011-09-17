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
import eu.delving.sip.model.SipModel;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JDesktopPane;
import javax.swing.JMenu;
import javax.swing.KeyStroke;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * Hold on to all the frames and manage their arrangemenbt
 *
 * @author Gerald de Jong
 */

public class AllFrames {
    private static Insets INSETS = new Insets(2, /* top */ 10, /* left */ 14, /* bottom */ 10 /* right */);
    private FrameBase[] frames;
    private Action[] views;
    private JDesktopPane desktop;
    private int viewIndex = 1;

    public AllFrames(JDesktopPane desktop, SipModel sipModel) {
        this.desktop = desktop;
        FrameBase status, analysis, create, statistics, input, fieldMapping, recordMapping, output;
        this.frames = new FrameBase[]{
                status = new StatusFrame(desktop, sipModel),
                analysis = new AnalysisFrame(desktop, sipModel),
                create = new CreateFrame(desktop, sipModel),
                statistics = new StatisticsFrame(desktop, sipModel),
                input = new InputFrame(desktop, sipModel),
                fieldMapping = new FieldMappingFrame(desktop, sipModel),
                recordMapping = new RecordMappingFrame(desktop, sipModel),
                output = new OutputFrame(desktop, sipModel)
        };
        this.views = new Action[]{
                view("First contact",
                        block(status, 0, 0),
                        block(analysis, 1, 0, 2, 1),
                        block(statistics, 3, 0, 2, 1)
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
                view("Deep code delving",
                        block(fieldMapping, 0, 0)
                ),
                view("Big picture",
                        block(input, 0, 0),
                        block(recordMapping, 1, 0),
                        block(output, 2, 0)
                ),
                view("Clear"),
                view("Show and tell",
                        block(analysis, 0, 0),
                        block(create, 0, 0),
                        block(statistics, 0, 0),
                        block(input, 0, 0),
                        block(recordMapping, 0, 0),
                        block(fieldMapping, 0, 0),
                        block(output, 0, 0),
                        block(status, 0, 0)
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

    private Arrangement view(String name, Block... blocks) {
        return new Arrangement(name, viewIndex++, blocks);
    }

    private Block block(FrameBase frame, int x, int y) {
        return block(frame, x, y, 1, 1);
    }

    private Block block(FrameBase frame, int x, int y, int w, int h) {
        return new Block(frame, x, y, w, h);
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

        void positionIn(int rows, int cols) {
            Dimension all = desktop.getSize();
            int wx = all.width / cols - (all.width % 2);
            int hx = all.height / rows - (all.height % 2);
            Point loc = new Point(x * wx - INSETS.left, y * hx - INSETS.top);
            Dimension size = new Dimension(w * wx + INSETS.left + INSETS.right, h * hx + INSETS.top + INSETS.bottom);
            frame.setLocation(loc);
            frame.setSize(size);
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
                    KeyStroke.getKeyStroke(KeyEvent.VK_0 + viewIndex, KeyEvent.ALT_MASK)
            );
            this.blocks = blocks;
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
                block.positionIn(rows, cols);
            }
            for (Block block : blocks) {
                block.frame.openFrame(false);
            }
        }
    }
}
