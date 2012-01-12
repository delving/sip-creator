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

import eu.delving.metadata.RecDef;
import eu.delving.sip.model.RecDefTreeNode;
import eu.delving.sip.model.SipModel;

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

/**
 * Handle the dragging and dropping of nodes
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class NodeTransferHandler extends TransferHandler {
    public static final DataFlavor FLAVOR = new DataFlavor(StatsTreeNode.class, "node");
    private SipModel sipModel;

    public NodeTransferHandler(SipModel sipModel) {
        this.sipModel = sipModel;
    }

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
        return new NodeTransferable(component);
    }

    @Override
    public boolean canImport(TransferHandler.TransferSupport info) {
        JTree targetTree = (JTree) info.getComponent();
//        System.out.println("transferable="+info.getTransferable());
//        System.out.println("component="+targetTree);
//        System.out.println("drop="+info.isDrop());
//        System.out.println("flavorsupported=" + info.isDataFlavorSupported(FLAVOR));
        if (targetTree.getDragEnabled()) {
            info.setShowDropLocation(false);
            return false;
        }
        else {
            return info.isDataFlavorSupported(FLAVOR);
        }
    }

    @Override
    public boolean importData(TransferHandler.TransferSupport info) {
        if (!canImport(info)) return false;
        try {
            sipModel.getCreateModel().setStatsTreeNode((StatsTreeNode) info.getTransferable().getTransferData(FLAVOR));
            JTree.DropLocation location = (JTree.DropLocation) info.getDropLocation();
            TreePath treePath = location.getPath();
            if (treePath.getLastPathComponent() instanceof RecDef.Ref) {
                sipModel.getCreateModel().setRecDefTreePath(((RecDef.Ref) treePath.getLastPathComponent()).path);
            }
            else if (treePath.getLastPathComponent() instanceof RecDefTreeNode) {
                sipModel.getCreateModel().setRecDefTreeNode((RecDefTreeNode) treePath.getLastPathComponent());
            }
            return true;
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
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
            return tree.getSelectionPath().getLastPathComponent();
        }
    }
}
