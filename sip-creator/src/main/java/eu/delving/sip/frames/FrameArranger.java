package eu.delving.sip.frames;

import eu.delving.sip.base.FrameBase;
import org.apache.log4j.Logger;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JDesktopPane;
import java.awt.Dimension;
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
        actions.add(new Exploration(frames.get("analysis"), frames.get("statistics"), frames.get("status")));
        actions.add(new DeepCodeDelving(frames.get("field-mapping")));
        actions.add(new Tweaking(frames.get("record-mapping"), frames.get("field-mapping"), frames.get("output")));
        actions.add(new ClickMapping(frames.get("create"), frames.get("record-mapping"), frames.get("statistics"), frames.get("output")));
        actions.add(new ShowOff(frames));
    }

    public List<Action> getActions() {
        return actions;
    }

    // Themed arrangements

    private class Exploration extends AbstractAction {

        private FrameBase analysisFrame;
        private FrameBase statisticsFrame;
        private FrameBase factsFrame;

        private Exploration(FrameBase analysisFrame, FrameBase statisticsFrame, FrameBase factsFrame) {
            super("Exploration");
            this.analysisFrame = analysisFrame;
            this.statisticsFrame = statisticsFrame;
            this.factsFrame = factsFrame;
        }

        public void arrange() {
            closeAllFrames();
            desktop.add(analysisFrame);
            desktop.add(statisticsFrame);
            desktop.add(factsFrame);
            desktop.getDesktopManager().openFrame(analysisFrame);
            desktop.getDesktopManager().openFrame(statisticsFrame);
            desktop.getDesktopManager().openFrame(factsFrame);
            createLeftDoubleRight(analysisFrame, statisticsFrame, factsFrame);
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

    private class Tweaking extends AbstractAction {

        private FrameBase recordMapping;
        private FrameBase fieldMapping;
        private FrameBase output;

        private Tweaking(FrameBase recordMapping, FrameBase fieldMapping, FrameBase output) {
            super("Tweaking Snippets");
            this.recordMapping = recordMapping;
            this.fieldMapping = fieldMapping;
            this.output = output;
        }

        private void arrange() {
            closeAllFrames();
            desktop.add(recordMapping);
            desktop.add(fieldMapping);
            desktop.add(output);
            desktop.getDesktopManager().openFrame(recordMapping);
            desktop.getDesktopManager().openFrame(fieldMapping);
            desktop.getDesktopManager().openFrame(output);
            createFace(recordMapping, fieldMapping, output);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            arrange();
        }
    }

    private class ClickMapping extends AbstractAction {

        private FrameBase createMapping;
        private FrameBase recordMapping; // todo: not sure if we will use this
        private FrameBase statistics;
        private FrameBase output;

        private ClickMapping(FrameBase createMapping, FrameBase recordMapping, FrameBase statistics, FrameBase output) {
            super("Click Mapping");
            this.createMapping = createMapping;
            this.recordMapping = recordMapping;
            this.statistics = statistics;
            this.output = output;
        }

        private void arrange() {
            closeAllFrames();
            desktop.add(createMapping);
            desktop.add(statistics);
            desktop.add(output);
            desktop.getDesktopManager().openFrame(createMapping);
            desktop.getDesktopManager().openFrame(statistics);
            desktop.getDesktopManager().openFrame(output);
            createFace(createMapping, statistics, output);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            arrange();
        }
    }

    /**
     * This view differs from the other views. It is meant for low resolution screens, and will add a new navigation
     * bar to the left. The selected windows will appear as full screen frames on the right part of the screen.
     */
    private class ShowOff extends AbstractAction {

        private Map<String, FrameBase> frames;

        private ShowOff(Map<String, FrameBase> frames) {
            super("Show Off");
            this.frames = frames;
        }

        private void arrange() {
            throw new NoSuchMethodError("Not implemented yet");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            arrange();
        }
    }

    private void createVerticalSplit(FrameBase left, FrameBase right) {
        Dimension desktopSize = desktop.getSize();
        desktop.getDesktopManager().setBoundsForFrame(left,
                0, 0, desktopSize.width / 3, desktopSize.height);
        desktop.getDesktopManager().setBoundsForFrame(right,
                left.getX() + left.getWidth(), 0, desktopSize.width / 3 * 2, desktopSize.height);
    }

    private void createFace(FrameBase leftEye, FrameBase rightEye, FrameBase mouth) {
        Dimension desktopSize = desktop.getSize();
        desktop.getDesktopManager().setBoundsForFrame(leftEye,
                0, 0, desktopSize.width / 2, desktopSize.height / 3 * 2);
        desktop.getDesktopManager().setBoundsForFrame(rightEye,
                leftEye.getX() + leftEye.getWidth(), 0, desktopSize.width / 2, desktopSize.height / 3 * 2);
        desktop.getDesktopManager().setBoundsForFrame(mouth,
                0, leftEye.getY() + leftEye.getHeight(), desktopSize.width, desktopSize.height / 3);
    }

    private void createLeftDoubleRight(FrameBase left, FrameBase rightUp, FrameBase rightBottom) {
        Dimension desktopSize = desktop.getSize();
        desktop.getDesktopManager().setBoundsForFrame(left,
                0, 0, desktopSize.width / 3, desktopSize.height);
        desktop.getDesktopManager().setBoundsForFrame(rightUp,
                left.getY() + left.getWidth(), 0, desktopSize.width / 3 * 2, desktopSize.height / 2);
        desktop.getDesktopManager().setBoundsForFrame(rightBottom,
                left.getY() + left.getWidth(), rightUp.getY() + rightUp.getHeight(), desktopSize.width / 3 * 2, desktopSize.height / 2);
    }

    private void closeAllFrames() {
        for (FrameBase frameBase : frames.values()) {
            desktop.getDesktopManager().closeFrame(frameBase);
        }
    }
}
