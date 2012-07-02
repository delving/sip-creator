/*
 * Copyright 2011, 2012 Delving BV
 *
 * Licensed under the EUPL, Version 1.0 or as soon they
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

import eu.delving.sip.base.CompileState;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.Swing;
import eu.delving.sip.model.MappingCompileModel;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

import static eu.delving.sip.base.SwingHelper.scrollVH;

/**
 * This frame shows the entire builder that is responsible for transforming the input to output,
 * as well as the constants and dictionaries that it may have.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class MappingCodeFrame extends FrameBase {
    private static int MARG = 30;
    public static final Font MONOSPACED = new Font("Monospaced", Font.BOLD, 10);
    private JTextArea recordArea = new JTextArea();
    private JTextArea fieldArea = new JTextArea();

    public MappingCodeFrame(JDesktopPane desktop, SipModel sipModel) {
        super(Which.CODE, desktop, sipModel, "Mapping Code", false);
        fieldArea.setFont(MONOSPACED);
        recordArea.setFont(MONOSPACED);
        Ear ear = new Ear();
        sipModel.getFieldCompileModel().addListener(ear);
        sipModel.getRecordCompileModel().addListener(ear);
        setPlacement(new Placement() {
            @Override
            public Point getLocation() {
                return new Point(MARG, MARG);
            }

            @Override
            public Dimension getSize() {
                return new Dimension(desktopPane.getSize().width - MARG * 2, desktopPane.getSize().height - MARG * 2);
            }
        });
        getAction().putValue(
                Action.ACCELERATOR_KEY,
                KeyStroke.getKeyStroke(KeyEvent.VK_M, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask())
        );
    }

    @Override
    protected void buildContent(Container content) {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Whole Record", scrollVH(recordArea));
        tabs.addTab("Current Mapping", scrollVH(fieldArea));
        content.add(tabs);
    }

    private class Ear implements MappingCompileModel.Listener {
        @Override
        public void stateChanged(CompileState state) {
            // nothing
        }

        @Override
        public void codeCompiled(MappingCompileModel.Type type, String code) {
            switch (type) {
                case RECORD:
                    exec(new CodeUpdater(code, recordArea));
                    break;
                case FIELD:
                    exec(new CodeUpdater(code, fieldArea));
                    break;
            }
        }
    }

    private static class CodeUpdater implements Swing {
        private String code;
        private JTextArea textArea;

        private CodeUpdater(String code, JTextArea textArea) {
            this.code = code;
            this.textArea = textArea;
        }

        @Override
        public void run() {
            if (code != null) {
                StringBuilder numbered = new StringBuilder();
                int index = 0;
                for (String line : code.split("\n")) {
                    numbered.append(String.format("%3d: ", ++index));
                    numbered.append(line).append('\n');
                }
                textArea.setText(numbered.toString());
            }
            else {
                textArea.setText("// No code");
            }
        }
    }

}
