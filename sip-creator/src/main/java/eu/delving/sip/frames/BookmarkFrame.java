/*
 * Copyright 2010 DELVING BV
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

import eu.delving.metadata.RecDef;
import eu.delving.metadata.RecMapping;
import eu.delving.sip.base.Exec;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.Utility;
import eu.delving.sip.model.BookmarksTreeModel;
import eu.delving.sip.model.MappingModel;
import eu.delving.sip.model.SipModel;
import org.antlr.stringtemplate.StringTemplate;

import javax.swing.DropMode;
import javax.swing.JDesktopPane;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.MouseEvent;

/**
 * The bookmarks containing refs to the rec def tree in the RecDefFrame.
 *
 * todo: use transferHandler for drag and drop
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class BookmarkFrame extends FrameBase {
    private JTree bookmarkTree;
    private RecDefFrame recDefFrame;

    public BookmarkFrame(JDesktopPane desktop, SipModel sipModel, RecDefFrame recDefFrame) {
        super(desktop, sipModel, "Bookmarks", false);
        this.recDefFrame = recDefFrame;
        sipModel.getMappingModel().addSetListener(new MappingModel.SetListener() {
            @Override
            public void recMappingSet(MappingModel mappingModel) {
                Exec.swing(new TreeUpdater());
            }
        });
        bookmarkTree = new JTree(new BookmarksTreeModel()) {
            @Override
            public String getToolTipText(MouseEvent evt) {
                TreePath treePath = bookmarkTree.getPathForLocation(evt.getX(), evt.getY());
                if (treePath == null) return "";
                Object last = treePath.getLastPathComponent();
                if (last instanceof RecDef.Category) {
                    RecDef.Category category = (RecDef.Category) last;
                    StringTemplate t = Utility.getTemplate("bookmark-category");
                    t.setAttribute("name", category.name);
                    t.setAttribute("doc", category.doc);
                    return t.toString();

                }
                else if (last instanceof RecDef.Ref) {
                    RecDef.Ref ref = (RecDef.Ref) last;
                    StringTemplate t = Utility.getTemplate(ref.isAttr() ? "bookmark-attribute" : "bookmark-element");
                    t.setAttribute("name", ref.display);
                    t.setAttribute("doc", ref.doc);
                    t.setAttribute("options", ref.options);
                    return t.toString();
                }
                else {
                    return "";
                }
            }
        };
        bookmarkTree.setRootVisible(false);
        bookmarkTree.setToolTipText("?");
        bookmarkTree.setDragEnabled(false);
        bookmarkTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        bookmarkTree.getSelectionModel().addTreeSelectionListener(new BookmarkSelection());
        bookmarkTree.setCellRenderer(new BookmarksTreeModel.BookmarkRenderer());
        bookmarkTree.setDropMode(DropMode.ON);
        bookmarkTree.setTransferHandler(sipModel.getNodeTransferHandler());
    }

    @Override
    protected void buildContent(Container content) {
        content.add(createBookmarkTreePanel());
    }

    private JPanel createBookmarkTreePanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.add(scroll(bookmarkTree), BorderLayout.CENTER);
        return p;
    }

    private class TreeUpdater implements Runnable {

        @Override
        public void run() {
            RecMapping recMapping = sipModel.getMappingModel().getRecMapping();
            if (recMapping != null) {
                bookmarkTree.setModel(new BookmarksTreeModel(recMapping.getRecDefTree().getRecDef().bookmarks));
            }
            else {
                bookmarkTree.setModel(new BookmarksTreeModel());
            }
        }
    }

    private class BookmarkSelection implements TreeSelectionListener {

        @Override
        public void valueChanged(TreeSelectionEvent event) {
            Object object = event.getPath().getLastPathComponent();
            if (object instanceof RecDef.Category) {
                if (bookmarkTree.isCollapsed(event.getPath())) {
                    bookmarkTree.expandPath(event.getPath());
                }
                else {
                    bookmarkTree.collapsePath(event.getPath());
                }
            }
            else if (object instanceof RecDef.Ref) {
                RecDef.Ref ref = (RecDef.Ref) object;
                recDefFrame.setPath(ref.path);
            }
        }
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(270, 300);
    }
}
