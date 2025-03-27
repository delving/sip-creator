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

package eu.delving.sip.base;

import eu.delving.sip.model.CreateModel;
import eu.delving.sip.model.RecDefTreeNode;
import eu.delving.sip.model.SipModel;
import eu.delving.sip.model.SourceTreeNode;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.TransferHandler;
import javax.swing.tree.TreePath;
import java.awt.Component;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Handle the dragging and dropping of nodes
 *
 *
 */

public class NodeTransferHandler extends TransferHandler {
    public static final DataFlavor FLAVOR = new DataFlavor(NodeListHolder.class, "nodeListHolder");
    private SipModel sipModel;

    public NodeTransferHandler(SipModel sipModel) {
        this.sipModel = sipModel;
    }

    @Override
    public Icon getVisualRepresentation(Transferable transferable) {
        return SwingHelper.ICON_VALUE;
    }

    @Override
    public int getSourceActions(javax.swing.JComponent jComponent) {
        return LINK;
    }

    @Override
    public Transferable createTransferable(final JComponent component) {
        return new NodeTransferable(component);
    }

    @Override
    public boolean canImport(TransferHandler.TransferSupport info) {
        JTree targetTree = (JTree) info.getComponent();
        if (targetTree.getDragEnabled()) {
            info.setShowDropLocation(false);
            return false;
        }
        else {
            return info.isDataFlavorSupported(FLAVOR);
        }
    }

    @Override
    public boolean importData(final TransferHandler.TransferSupport info) {
        if (!canImport(info)) return false;
        try {
            final NodeListHolder nodeListHolder = (NodeListHolder) info.getTransferable().getTransferData(FLAVOR);
            sipModel.exec(new Work() {
                @Override
                public void run() {
                    CreateModel createModel = sipModel.getCreateModel();
                    createModel.setSource(nodeListHolder.nodeSet);
                    JTree.DropLocation location = (JTree.DropLocation) info.getDropLocation();
                    TreePath treePath = location.getPath();
                    if (treePath.getLastPathComponent() instanceof RecDefTreeNode) {
                        RecDefTreeNode recDefTreeNode = (RecDefTreeNode) treePath.getLastPathComponent();
                        createModel.setTarget(recDefTreeNode);
                        if (createModel.canCreate()) createModel.createMapping();
                    }
                }

                @Override
                public Job getJob() {
                    return Job.DROP_TRANSFER_HANDLER;
                }
            });
            return true;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private class NodeTransferable implements Transferable {
        private Component component;

        private NodeTransferable(Component component) {
            this.component = component;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{FLAVOR};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor dataFlavor) {
            return dataFlavor.equals(FLAVOR);
        }

        @Override
        public Object getTransferData(DataFlavor dataFlavor) throws UnsupportedFlavorException, IOException {
            JTree tree = (JTree) component;
            return new NodeListHolder(tree.getSelectionPaths());
        }
    }

    public static class NodeListHolder {
        public SortedSet<SourceTreeNode> nodeSet = new TreeSet<SourceTreeNode>();

        public NodeListHolder(TreePath[] selectionPaths) {
            for (TreePath path : selectionPaths) nodeSet.add((SourceTreeNode) path.getLastPathComponent());
        }
    }
}
