package eu.delving.sip.menus;

import eu.delving.sip.frames.AllFrames;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import static eu.delving.sip.files.Storage.TELEMETRY_ENABLED;

/**
 * A menu for help-related options and actions.
 *
 */
public class HelpMenu extends JMenu {
    private final SipModel sipModel;
    private final AllFrames allFrames;

    public HelpMenu(SipModel sipModel, AllFrames allFrames, boolean isTelemetryIncluded) {
        super("Help");
        this.sipModel = sipModel;
        this.allFrames = allFrames;
        if (isTelemetryIncluded) {
            add(new TelemetryMenuItem());
        }
    }

    private class TelemetryMenuItem extends JCheckBoxMenuItem implements ItemListener {
        private final boolean isTelemetryEnabled;

        public TelemetryMenuItem() {
            super("Enable telemetry");
            isTelemetryEnabled = sipModel.getPreferences().getProperty(TELEMETRY_ENABLED, "false").contentEquals("true");
            setSelected(isTelemetryEnabled);
            addItemListener(this);
        }

        @Override
        public void itemStateChanged(ItemEvent e) {
            int state = e.getStateChange();
            //boolean selected = state == ItemEvent.SELECTED;

            // Clear the telemetry setting
            sipModel.getPreferences().remove(TELEMETRY_ENABLED);
            sipModel.saveProperties();
            JOptionPane.showMessageDialog(sipModel.getDesktop(), "Please restart the application now to change the telemetry setting.",
                    "Telemetry", JOptionPane.INFORMATION_MESSAGE);

            // Restore the selections state to the original setting
            removeItemListener(this);
            setState(isTelemetryEnabled);
            addItemListener(this);
        }
    }

}
