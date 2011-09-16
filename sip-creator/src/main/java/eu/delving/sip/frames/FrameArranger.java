package eu.delving.sip.frames;

import eu.delving.sip.base.FrameBase;
import org.apache.log4j.Logger;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JDesktopPane;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Arrange frames automatically.
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public class FrameArranger {

    private final static Logger LOG = Logger.getLogger(FrameArranger.class);
    private List<Action> actions = new ArrayList<Action>();
    private JDesktopPane desktop;
    private Map<String, FrameBase> frames;

    public FrameArranger(JDesktopPane desktop, Map<String, FrameBase> frames) {
        this.desktop = desktop;
        this.frames = frames;
        createActions();
    }

    private void createActions() {
        actions.add(new Exploration(frames.get("analysis"), frames.get("statistics")));
        actions.add(new DeepCodeDelving(frames.get("field-mapping")));
    }

    public List<Action> getActions() {
        return actions;
    }

    // Themed arrangements

    private class Exploration extends AbstractAction {

        private FrameBase analysisFrame;
        private FrameBase statisticsFrame;

        private Exploration(FrameBase analysisFrame, FrameBase statisticsFrame) {
            super("Exploration");
            this.analysisFrame = analysisFrame;
            this.statisticsFrame = statisticsFrame;
        }

        public void arrange() {
            closeAllFrames();
            desktop.add(analysisFrame);
            desktop.add(statisticsFrame);
            desktop.getDesktopManager().openFrame(analysisFrame);
            desktop.getDesktopManager().openFrame(statisticsFrame);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            arrange();
        }
    }

    private class DeepCodeDelving extends AbstractAction {

        private FrameBase fieldMapping;

        private DeepCodeDelving(FrameBase fieldMapping) {
            super("Deep Code Delving");
            this.fieldMapping = fieldMapping;
        }

        private void arrange() {
            closeAllFrames();
            desktop.add(fieldMapping);
            desktop.getDesktopManager().openFrame(fieldMapping);
            desktop.getDesktopManager().maximizeFrame(fieldMapping);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            arrange();
        }
    }

    private void closeAllFrames() {
        for (FrameBase frameBase : frames.values()) {
            desktop.getDesktopManager().closeFrame(frameBase);
        }
    }
}
