package eu.delving.sip.xml;

import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.util.List;

/**
* todo: javadoc
*
* @author Gerald de Jong <geralddejong@gmail.com>
*/
class BookmarksTreeModel implements TreeModel {
    private List<RecDef.Category> bookmarks;

    BookmarksTreeModel(List<RecDef.Category> bookmarks) {
        this.bookmarks = bookmarks;
    }

    @Override
    public Object getRoot() {
        return bookmarks;
    }

    @Override
    public Object getChild(Object object, int index) {
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
}
