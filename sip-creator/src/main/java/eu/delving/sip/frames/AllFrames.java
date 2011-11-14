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

import eu.delving.sip.base.Exec;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.menus.EditHistory;
import eu.delving.sip.model.DataSetModel;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import static eu.delving.sip.base.FrameBase.INSETS;

/**
 * Hold on to all the frames and manage their arrangemenbt
 *
 * @author Gerald de Jong
 */

public class AllFrames {
    private final String VIEW_PREF = "currentView";
    private Dimension LARGE_ICON_SIZE = new Dimension(80, 50);
    private Dimension SMALL_ICON_SIZE = new Dimension(30, 18);
    private FrameBase[] frames;
    private Arrangement[] views;
    private JDesktopPane desktop;
    private int viewIndex = 1;
    private View current = View.CLEAR;
    private SipModel sipModel;

    private enum View {
        CLEAR("Clear"),
        FIRST_CONTACT("First contact"),
        QUICK_MAPPING("Quick mapping"),
        BIG_PICTURE("Big Picture"),
        CODE_TWEAKING("Code tweaking"),
        DEEP_DELVING("Deep delving"),
        DECADENT_DISPLAY("Decadant Display"),
        PROJECTOR("Projector");

        private String name;

        View(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public AllFrames(JDesktopPane desktop, final SipModel sipModel, EditHistory editHistory) {
        this.desktop = desktop;
        this.sipModel = sipModel;
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
                view(View.FIRST_CONTACT,
                        block(analysis, 0, 0, 1, 3),
                        block(statistics, 1, 0, 1, 2),
                        block(status, 1, 2)
                ),
                view(View.QUICK_MAPPING,
                        block(create, 0, 0),
                        block(statistics, 1, 0),
                        block(recordMapping, 2, 0)
                ),
                view(View.CODE_TWEAKING,
                        block(recordMapping, 0, 0),
                        block(fieldMapping, 1, 0, 3, 1),
                        block(input, 4, 0)
                ),
                view(View.DEEP_DELVING,
                        block(fieldMapping, 0, 0)
                ),
                view(View.BIG_PICTURE,
                        block(input, 0, 0),
                        block(recordMapping, 1, 0),
                        block(output, 2, 0)
                ),
                view(View.DECADENT_DISPLAY,
                        block(create, 0, 0, 3, 3),
                        block(statistics, 0, 3, 2, 3),
                        block(input, 5, 0, 2, 4),
                        block(recordMapping, 3, 0, 2, 3),
                        block(fieldMapping, 2, 3, 3, 5),
                        block(output, 5, 4, 2, 4),
                        block(status, 0, 6, 2, 2)
                ),
                view(View.PROJECTOR,
                        block(analysis, 0, 0),
                        block(create, 0, 0),
                        block(statistics, 0, 0),
                        block(input, 0, 0),
                        block(recordMapping, 0, 0),
                        block(fieldMapping, 0, 0),
                        block(output, 0, 0),
                        block(status, 0, 0)
                ),
        };
        sipModel.getDataSetModel().addListener(new DataSetModel.Listener() {
            @Override
            public void dataSetChanged(DataSet dataSet) {
                dataSetStateChanged(dataSet, dataSet.getState());
            }

            @Override
            public void dataSetRemoved() {
            }

            @Override
            public void dataSetStateChanged(DataSet dataSet, DataSetState dataSetState) {
                switch (dataSetState) {
                    case EMPTY:
                        select(View.CLEAR);
                        break;
                    case IMPORTED:
                    case ANALYZED_IMPORT:
                    case SOURCED:
                    case DELIMITED:
                        switch (current) {
                            case CLEAR:
                            case QUICK_MAPPING:
                            case CODE_TWEAKING:
                            case DEEP_DELVING:
                            case BIG_PICTURE:
                            case DECADENT_DISPLAY:
                                select(View.FIRST_CONTACT);
                                break;
                            case FIRST_CONTACT:
                            case PROJECTOR:
                                break;
                            default:
                                throw new RuntimeException();
                        }
                        break;
                    case ANALYZED_SOURCE:
                    case MAPPING:
                    case VALIDATED:
                        switch (current) {
                            case CLEAR:
                            case FIRST_CONTACT:
                                select(View.QUICK_MAPPING);
                                break;
                            case QUICK_MAPPING:
                            case CODE_TWEAKING:
                            case DEEP_DELVING:
                            case BIG_PICTURE:
                            case DECADENT_DISPLAY:
                            case PROJECTOR:
                                break;
                            default:
                                throw new RuntimeException();
                        }
                        break;
                }
            }
        });
    }

    public void restore() {
        Exec.swingLater(new Runnable() {
            @Override
            public void run() {
                select(View.valueOf(sipModel.getPreferences().get(VIEW_PREF, View.CLEAR.toString())));
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

    private void select(View view) {
        if (current == view) return;
        for (Arrangement arrangement : views) {
            if (arrangement.view == view) {
                arrangement.actionPerformed(null);
                sipModel.getFeedback().say(String.format("Selecting view %s", view.getName()));
                return;
            }
        }
        for (FrameBase frame : frames) {
            frame.closeFrame();
        }
        current = View.CLEAR;
    }

    private Arrangement view(View view, Block... blocks) {
        return new Arrangement(view, viewIndex++, blocks);
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
        View view;
        Block[] blocks;

        Arrangement(View view, int viewIndex, Block[] blocks) {
            super(view.getName());
            this.view = view;
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
                block.frame.setLocation(situation.location);
                block.frame.setSize(situation.size);
            }
            for (Block block : blocks) {
                block.frame.openFrame();
            }
            sipModel.getPreferences().put(VIEW_PREF, (current = view).toString());
        }

    }

    private class ViewIcon implements Icon {
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
                g.drawRect(x + situation.location.x + 1, y + situation.location.y + 1, situation.size.width, situation.size.height);
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

}
