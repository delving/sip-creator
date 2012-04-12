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

import eu.delving.metadata.*;
import eu.delving.sip.base.Exec;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.Utility;
import eu.delving.sip.model.*;
import eu.delving.sip.panels.HtmlPanel;
import org.antlr.stringtemplate.StringTemplate;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.event.*;
import java.util.List;

/**
 * Render the record definition as a JTree
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TargetFrame extends FrameBase {
    private JTree recDefTree;
    private JTree bookmarkTree;
    private HtmlPanel bookmarkDocPanel = new HtmlPanel("Documentation");
    private JTextField filterField = new JTextField();
    private JPanel treePanel = new JPanel(new GridLayout(0, 1));
    private JCheckBox hideAttributes = new JCheckBox("Hide Attributes");
    private JCheckBox autoFoldBox = new JCheckBox("Auto-Fold");
    private Timer timer = new Timer(300, new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            Object model = recDefTree.getModel();
            if (model instanceof FilterTreeModel) {
                FilterTreeModel ftm = (FilterTreeModel) model;
                ftm.setFilter(filterField.getText().trim());
            }
        }
    });

    public TargetFrame(JDesktopPane desktop, SipModel sipModel) {
        super(Which.TARGET, desktop, sipModel, "Target", false);
        sipModel.getMappingModel().addSetListener(new MappingModel.SetListener() {
            @Override
            public void recMappingSet(MappingModel mappingModel) {
                Exec.swing(new TreeUpdater(mappingModel.hasRecMapping() ? mappingModel.getRecMapping().getPrefix() : "?"));
            }
        });
        createRecDefTree(sipModel);
        createBookmarkTree(sipModel);
        timer.setRepeats(false);
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent documentEvent) {
                timer.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent documentEvent) {
                timer.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent documentEvent) {
                timer.restart();
            }
        });
        filterField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent focusEvent) {
                String text = filterField.getText();
                filterField.setSelectionStart(0);
                filterField.setSelectionEnd(text.length());
            }
        });
        hideAttributes.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent itemEvent) {
                if (recDefTree.getModel().getRoot() instanceof RecDefTreeNode) {
                    FilterTreeModel filterTreeModel = (FilterTreeModel) recDefTree.getModel();
                    filterTreeModel.setAttributesHidden(hideAttributes.isSelected());
                    showPath((RecDefTreeNode) filterTreeModel.getRoot());
                }
            }
        });
    }

    @Override
    protected void buildContent(Container content) {
        content.add(createFilterPanel(), BorderLayout.NORTH);
        content.add(treePanel, BorderLayout.CENTER);
    }

    private JPanel createFilterPanel() {
        JPanel p = new JPanel(new BorderLayout(10, 10));
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEtchedBorder(), BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        p.add(new JLabel("Filter:", JLabel.RIGHT), BorderLayout.WEST);
        p.add(filterField, BorderLayout.CENTER);
        JPanel pp = new JPanel(new GridLayout(1, 0));
        pp.add(hideAttributes);
        pp.add(autoFoldBox);
        p.add(pp, BorderLayout.EAST);
        return p;
    }

    private class RecDefSelection implements TreeSelectionListener, Runnable {

        private Object nodeObject;

        @Override
        public void valueChanged(TreeSelectionEvent event) {
            nodeObject = event.getPath().getLastPathComponent();
            Exec.work(this);
        }

        @Override
        public void run() {
            if (nodeObject instanceof RecDefTreeNode) {
                RecDefTreeNode node = (RecDefTreeNode) nodeObject;
                if (autoFoldBox.isSelected()) showPath(node);
                if (node.getRecDefNode().isUnmappable()) return;
                sipModel.getCreateModel().setRecDefTreeNode(node);
            }
            else {
                sipModel.getCreateModel().setRecDefTreeNode(null);
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
                bookmarkDocPanel.setHtml(docToHtml(ref.doc));
                recDefTree.setSelectionPath(sipModel.getMappingModel().getTreePath(ref.outputPath));
            }
        }
    }

    private String docToHtml(List<String> doc) {
        StringBuilder html = new StringBuilder("<html><p>");
        if (doc == null) {
            html.append("No documentation");
        }
        else {
            for (String line : doc) html.append(line).append('\n');
        }
        html.append("</p></html>");
        return html.toString();
    }

    private void showPath(RecDefTreeNode node) {
        RecDefTreeNode root = (RecDefTreeNode) recDefTree.getModel().getRoot();
        root.showPath(recDefTree, node.getRecDefPath().getTagPath());
    }

    private void createBookmarkTree(SipModel sipModel) {
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
                    t.setAttribute("opts", ref.opts);
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

    private void createRecDefTree(SipModel sipModel) {
        recDefTree = new JTree(new FilterTreeModel(FilterNode.createMessageNode("Empty"))) {
            @Override
            public String getToolTipText(MouseEvent evt) {
                TreePath treePath = recDefTree.getPathForLocation(evt.getX(), evt.getY());
                return treePath != null ? ((RecDefTreeNode) treePath.getLastPathComponent()).toHtml() : "";
            }
        };
        recDefTree.setToolTipText("?");
        recDefTree.setCellRenderer(new RecDefTreeNode.Renderer());
        recDefTree.setDragEnabled(false);
        recDefTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        recDefTree.getSelectionModel().addTreeSelectionListener(new RecDefSelection());
        recDefTree.setDropMode(DropMode.ON);
        recDefTree.setTransferHandler(sipModel.getNodeTransferHandler());
        sipModel.getMappingModel().addChangeListener(new MappingModel.ChangeListener() {
            @Override
            public void functionChanged(MappingModel mappingModel, MappingFunction function) {
            }

            @Override
            public void nodeMappingChanged(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
            }

            @Override
            public void nodeMappingAdded(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
                refreshRecDefTreeNode(mappingModel, node);
            }

            @Override
            public void nodeMappingRemoved(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
                refreshRecDefTreeNode(mappingModel, node);
            }

            private void refreshRecDefTreeNode(final MappingModel mappingModel, final RecDefNode node) {
                mappingModel.getRecDefTreeRoot().getRecDefTreeNode(node).fireChanged();
            }
        });
    }

    private class TreeUpdater implements Runnable {
        private String prefix;

        private TreeUpdater(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public void run() {
            RecDefTreeNode root = sipModel.getMappingModel().getRecDefTreeRoot();
            if (root != null) {
                FilterTreeModel filterTreeModel = new FilterTreeModel(root);
                recDefTree.setModel(filterTreeModel);
                filterTreeModel.setAttributesHidden(hideAttributes.isSelected());
                showPath(root);
            }
            else {
                recDefTree.setModel(new FilterTreeModel(FilterNode.createMessageNode("No record definition")));
            }
            boolean bookmarksPresent = false;
            RecMapping recMapping = sipModel.getMappingModel().getRecMapping();
            if (recMapping != null) {
                RecDef recDef = recMapping.getRecDefTree().getRecDef();
                bookmarksPresent = !recDef.bookmarks.isEmpty();
                bookmarkTree.setModel(new BookmarksTreeModel(recDef.bookmarks));
            }
            else {
                bookmarkTree.setModel(new BookmarksTreeModel());
            }
            treePanel.removeAll();
            treePanel.add(Utility.scrollVH(String.format("Record Definition for \"%s\"", prefix.toUpperCase()), recDefTree));
            if (bookmarksPresent) {
                JPanel p = new JPanel(new GridLayout(1, 0));
                p.add(Utility.scrollVH("Bookmarks", bookmarkTree));
                p.add(bookmarkDocPanel);
                treePanel.add(p);
            }
        }
    }

}
