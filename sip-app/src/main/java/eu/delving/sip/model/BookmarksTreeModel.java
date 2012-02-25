package eu.delving.sip.model;

import eu.delving.metadata.RecDef;
import eu.delving.sip.base.Utility;

import javax.swing.JTree;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.Component;
import java.util.List;

/**
* The tree of bookmarks
*
* @author Gerald de Jong <geralddejong@gmail.com>
*/

public class BookmarksTreeModel implements TreeModel {
    private List<RecDef.Category> bookmarks;

    public BookmarksTreeModel() {
    }

    public BookmarksTreeModel(List<RecDef.Category> bookmarks) {
        this.bookmarks = bookmarks;
    }

    @Override
    public Object getRoot() {
        return bookmarks;
    }

    @Override
    public Object getChild(Object object, int index) {
        if (bookmarks == null) return "Empty";
        if (object == bookmarks) {
            return bookmarks.get(index);
        }
        else if (object instanceof RecDef.Category) {
            RecDef.Category category = (RecDef.Category)object;
            return category.refs.get(index);
        }
        else {
            return null;
        }
    }

    @Override
    public int getChildCount(Object object) {
        if (object == bookmarks) {
            return bookmarks.size();
        }
        else if (object instanceof RecDef.Category) {
            RecDef.Category category = (RecDef.Category)object;
            return category.refs.size();
        }
        else {
            return 0;
        }
    }

    @Override
    public boolean isLeaf(Object object) {
        return object != bookmarks && !(object instanceof RecDef.Category);
    }

    @Override
    public void valueForPathChanged(TreePath treePath, Object o) {
    }

    @Override
    public int getIndexOfChild(Object parent, Object child) {
        if (parent == bookmarks) {
            return bookmarks.indexOf(child);
        }
        else if (parent instanceof RecDef.Category) {
            RecDef.Category category = (RecDef.Category)parent;
            return category.refs.indexOf(child);
        }
        else {
            return 0;
        }
    }

    @Override
    public void addTreeModelListener(TreeModelListener treeModelListener) {
    }

    @Override
    public void removeTreeModelListener(TreeModelListener treeModelListener) {
    }

    public static class BookmarkRenderer extends DefaultTreeCellRenderer {

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            Component component = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            if (value instanceof RecDef.Category) {
                setIcon(expanded ? Utility.BOOKMARK_EXPANDED_ICON : Utility.BOOKMARK_ICON);
            }
            else if (value instanceof RecDef.Ref) {
                RecDef.Ref ref = (RecDef.Ref) value;
                if (ref.isAttr()) {
                    setIcon(Utility.ATTRIBUTE_ICON);
                }
                else if (!ref.elem.elemList.isEmpty()) {
                    setIcon(Utility.COMPOSITE_ELEMENT_ICON);
                }
                else {
                    setIcon(Utility.VALUE_ELEMENT_ICON);
                }
            }
            return component;
        }
    }


}
