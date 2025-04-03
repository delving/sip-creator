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

package eu.delving.sip.menus;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.frames.AllFrames;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import static eu.delving.sip.files.Storage.THEME_MODE;

/**
 * Simple theme menu for switching between light and dark modes in FlatLaf (manual only).
 *
 */
public class ThemeMenu extends JMenu {
    private final SipModel sipModel;
    private final AllFrames allFrames;
    private final LightMenuItem lightMenuItem;
    private final DarkMenuItem darkMenuItem;

    public ThemeMenu(SipModel sipModel, AllFrames allFrames) {
        super("Theme");
        this.sipModel = sipModel;
        this.allFrames = allFrames;
        ButtonGroup bg = new ButtonGroup();
        bg.add(lightMenuItem = new LightMenuItem());
        bg.add(darkMenuItem = new DarkMenuItem());
        add(lightMenuItem);
        add(darkMenuItem);
    }

    private String getMode() {
        return sipModel.getAppPreferences().getProperty(THEME_MODE, "light");
    }

    private void setMode(String mode) {
        sipModel.getAppPreferences().setProperty(THEME_MODE, mode);
        sipModel.saveAppProperties();
        switch (mode) {
            case "light":
                darkMenuItem.setSelected(false);
                try {
                    UIManager.setLookAndFeel(new FlatLightLaf());
                } catch (UnsupportedLookAndFeelException e) {
                }
                refreshTheme(mode);
                break;
            case "dark":
                lightMenuItem.setSelected(false);
                try {
                    UIManager.setLookAndFeel(new FlatDarkLaf());
                } catch (UnsupportedLookAndFeelException e) {
                }
                refreshTheme(mode);
                break;
        }
    }

    /**
     * Do whatever it takes to refresh the theme and return to the previous view state.
     */
    private void refreshTheme(String mode) {
        for (FrameBase frame : allFrames.getFrames()) {
            frame.openFrame();
            frame.setVisible(false);
        }
        FlatLaf.updateUI();
        for (FrameBase frame : allFrames.getFrames()) {
            frame.setFrameIcon(null);
        }
        for (FrameBase frame : allFrames.getFrames()) {
            frame.setTheme(mode);
        }
        sipModel.getViewSelector().refreshView();
    }

    private class LightMenuItem extends JRadioButtonMenuItem implements ItemListener {
        public LightMenuItem() {
            super("Light");
            boolean state = sipModel.getAppPreferences().getProperty(THEME_MODE, "light").contentEquals("light");
            setSelected(state);
            addItemListener(this);
        }

        @Override
        public void itemStateChanged(ItemEvent e) {
            int state = e.getStateChange();
            boolean selected = state == ItemEvent.SELECTED;
            setMode(selected ? "light" : "dark");
        }
    }

    private class DarkMenuItem extends JRadioButtonMenuItem implements ItemListener {
        public DarkMenuItem() {
            super("Dark");
            boolean state = sipModel.getAppPreferences().getProperty(THEME_MODE, "light").contentEquals("dark");
            setSelected(state);
            addItemListener(this);
        }

        @Override
        public void itemStateChanged(ItemEvent e) {
            int state = e.getStateChange();
            boolean selected = state == ItemEvent.SELECTED;
            setMode(selected ? "dark" : "light");
        }
    }

}
