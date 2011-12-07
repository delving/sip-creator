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

package eu.delving.sip.base;

import eu.delving.sip.model.RecDefTreeNode;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

/**
 * Handle the dragging and dropping of nodes
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class NodeTransferHandler extends TransferHandler {
    public static final DataFlavor FLAVOR = new DataFlavor(StatsTreeNode.class, "node");

    @Override
    public Icon getVisualRepresentation(Transferable transferable) {
        StatsTreeNode node;
        try {
            node = (StatsTreeNode) transferable.getTransferData(FLAVOR);
            if (node.getTag().isAttribute()) {
                return Utility.ATTRIBUTE_ICON;
            }
            else if (node.getChildNodes().iterator().hasNext()) {
                return Utility.COMPOSITE_ELEMENT_ICON;
            }
            else {
                return Utility.VALUE_ELEMENT_ICON;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public int getSourceActions(javax.swing.JComponent jComponent) {
        return LINK;
    }

    @Override
    public Transferable createTransferable(final JComponent component) {
        return new Transferable() {
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
                return tree.getSelectionPath().getLastPathComponent();
            }
        };
    }

    @Override
    public boolean canImport(TransferHandler.TransferSupport info) {
//             if (!info.isDataFlavorSupported(DataFlavor.stringFlavor))
//             JTree.DropLocation dl = (JTree.DropLocation)info.getDropLocation();
//             TreePath path = dl.getPath();
        return true;
    }

    @Override
    public boolean importData(TransferHandler.TransferSupport info) {
        if (!canImport(info)) return false;
        try {
            final StatsTreeNode node = (StatsTreeNode) info.getTransferable().getTransferData(FLAVOR);
            JTree.DropLocation location = (JTree.DropLocation) info.getDropLocation();
            TreePath path = location.getPath();
            final RecDefTreeNode target = (RecDefTreeNode) path.getLastPathComponent();
            Exec.work(new Runnable() {
                @Override
                public void run() {
                    try {
                        target.setStatsTreeNode(node);
                        System.out.println("Dropped " + node + " into " + target);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
            return true;
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
