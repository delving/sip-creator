package eu.delving.sip.model;

import javax.swing.event.EventListenerList;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic filter tree model
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class FilterTreeModel implements TreeModel {
    private EventListenerList listeners = new EventListenerList();
    private FilterNode root;

    public FilterTreeModel(FilterNode root) {
        this.root = root;
        root.setTreeModel(this);
    }

    public void setFilter(String patternString) {
        node(getRoot()).filter(patternString);
        refreshTree();
    }

    public void setRoot(FilterNode root) {
        this.root = root;
        root.setTreeModel(this);
        refreshTree();
    }

    @Override
    public Object getRoot() {
        return root;
    }

    @Override
    public Object getChild(Object nodeObject, int index) {
        if (index < 0) return null;
        return filterChildren(nodeObject).get(index);
    }

    @Override
    public int getChildCount(Object nodeObject) {
        FilterNode node = node(nodeObject);
        int count = 0;
        for (FilterNode sub : node.getChildren()) if (sub.passesFilter()) count++;
        return count;
    }

    @Override
    public boolean isLeaf(Object nodeObject) {
        FilterNode node = node(nodeObject);
        return node.isLeaf();
    }

    @Override
    public void valueForPathChanged(TreePath treePath, Object o) {
        // values don't change
    }

    @Override
    public int getIndexOfChild(Object parentObject, Object childObject) {
        return filterChildren(parentObject).indexOf(node(childObject));
    }

    @Override
    public void addTreeModelListener(TreeModelListener treeModelListener) {
        listeners.add(TreeModelListener.class, treeModelListener);
    }

    @Override
    public void removeTreeModelListener(TreeModelListener treeModelListener) {
        listeners.remove(TreeModelListener.class, treeModelListener);
    }

    public void refreshTree() {
        TreeModelEvent event = new TreeModelEvent(this, new Object[] {root}, new int[]  {}, new Object[] {});
        Object[] ears = listeners.getListenerList();
        for (int walk = ears.length - 2; walk >= 0; walk -= 2) {
            if (ears[walk] == TreeModelListener.class) {
                ((TreeModelListener) ears[walk + 1]).treeStructureChanged(event);
            }
        }
    }

    public void refreshNode(Object nodeObject) {
        FilterNode child = (FilterNode) nodeObject;
        FilterNode parent = (FilterNode)child.getParent();
        int index = getIndexOfChild(parent, child);
        TreeModelEvent event = new TreeModelEvent(this, getPathToRoot(parent), new int[] { index }, new Object[] {child});
        Object[] ears = listeners.getListenerList();
        for (int walk = ears.length - 2; walk >= 0; walk -= 2) {
            if (ears[walk] == TreeModelListener.class) {
                ((TreeModelListener) ears[walk + 1]).treeNodesChanged(event);
            }
        }
    }

    private Object[] getPathToRoot(FilterNode node) {
        List<Object> path = new ArrayList<Object>();
        path.add(node);
        while (node.getParent() != null) {
            node = (FilterNode)node.getParent();
            path.add(0, node);
        }
        return path.toArray();
    }

    private FilterNode node(Object nodeObject) {
        return (FilterNode) nodeObject;
    }

    private List<FilterNode> filterChildren(Object nodeObject) {
        List<FilterNode> filtered = new ArrayList<FilterNode>();
        for (FilterNode sub : node(nodeObject).getChildren()) if (sub.passesFilter()) filtered.add(sub);
        return filtered;
    }

}
