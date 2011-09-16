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
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ActionEvent;

/**
 * Hold on to all the frames and manage their arrangemenbt
 *
 * @author Gerald de Jong
 */

public class AllFrames {
    private FrameBase[] frames;
    private StatusFrame status;
    private AnalysisFrame analysis;
    private CreateFrame create;
    private StatisticsFrame statistics;
    private InputFrame input;
    private FieldMappingFrame fieldMapping;
    private RecordMappingFrame recordMapping;
    private OutputFrame output;
    private JDesktopPane desktop;

    public AllFrames(JDesktopPane desktop, SipModel sipModel) {
        this.desktop = desktop;
        this.frames = new FrameBase[]{
                this.status = new StatusFrame(desktop, sipModel),
                this.analysis = new AnalysisFrame(desktop, sipModel),
                this.create = new CreateFrame(desktop, sipModel),
                this.statistics = new StatisticsFrame(desktop, sipModel),
                this.input = new InputFrame(desktop, sipModel),
                this.fieldMapping = new FieldMappingFrame(desktop, sipModel),
                this.recordMapping = new RecordMappingFrame(desktop, sipModel),
                this.output = new OutputFrame(desktop, sipModel)
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
        for (Action action : getActions()) {
            menu.add(action);
        }
        return menu;
    }

    private Arrangement action(String name, Block... blocks) {
        return new Arrangement(name, blocks);
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
            int wx = all.width / cols;
            int hx = all.height / rows;
            Point loc = new Point(x * wx, 10 + y * hx);
            Dimension size = new Dimension(w * wx, h * hx);
            System.out.printf("%d, %d, %d, %d\n", loc.x, loc.y, size.width, size.height);
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

        Arrangement(String name, Block[] blocks) {
            super(name);
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

    private Action[] getActions() {
        return new Action[]{
                action("Exploration",
                        block(analysis, 0, 0),
                        block(statistics, 1, 0)
                ),
                action("Click Mapping",
                        block(create, 0, 0),
                        block(statistics, 1, 0),
                        block(output, 2, 0)
                ),
                action("Snippet Tweaking",
                        block(recordMapping, 0, 0),
                        block(fieldMapping, 1, 0)
                ),
                action("Deep Delving",
                        block(fieldMapping, 0, 0)
                ),
                action("Showoff",
                        block(status, 0, 0),
                        block(analysis, 0, 0),
                        block(create, 0, 0),
                        block(statistics, 0, 0),
                        block(input, 0, 0),
                        block(recordMapping, 0, 0),
                        block(fieldMapping, 0, 0),
                        block(output, 0, 0)
                )
        };
    }
}
