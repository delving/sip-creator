package eu.delving.sip.base;

import eu.delving.sip.model.SipModel;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionEvent;

/**
 * §
 * A visual representation of the HarvestPool, the details of each running harvest are visible and
 * the individual harvests are cancellable.
 */
public class HarvestDialog extends FrameBase {

    private static final Dimension SIZE = new Dimension(550, 300);
    private JButton cancel = new JButton(new CancelAction());
    private JList harvestList;
    private HarvestPool harvestPool;

    public HarvestDialog(JComponent parent, SipModel sipModel, HarvestPool harvestPool) {
        super(parent, sipModel, "Active harvests", false);
        this.harvestPool = harvestPool;
        setSize(SIZE);
    }

    @Override
    protected void buildContent(Container content) {
        harvestList = new JList(harvestPool);
        harvestList.addListSelectionListener(
                new ListSelectionListener() {
                    @Override
                    public void valueChanged(ListSelectionEvent listSelectionEvent) {
                        cancel.setEnabled(!harvestList.isSelectionEmpty());
                    }
                }
        );
        setLayout(new BorderLayout());
        content.add(new JScrollPane(harvestList), BorderLayout.CENTER);
        content.add(cancel, BorderLayout.SOUTH);
    }

    @Override
    protected void refresh() {
    }

    public void openAtPosition() {
        setLocation(desktopPane.getSize().width - getWidth() + 8, desktopPane.getSize().height - getHeight() + 8);
        setSize(SIZE);
        openFrame(false);
    }

    private class CancelAction extends AbstractAction {

        private CancelAction() {
            super("Cancel harvest");
            setEnabled(false);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            Harvestor harvestor = (Harvestor) harvestList.getSelectedValue();
            harvestor.setCancelled(true);
        }
    }
}
