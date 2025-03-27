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

package eu.delving.sip.model;

import eu.delving.sip.base.Swing;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Superclass to handle the filtering of tree nodes, both source and target.
 *
 *
 */

public abstract class FilterNode {
    protected FilterTreeModel filterModel;
    protected boolean highlighted = false;
    protected boolean passesFilter = true;

    void setFilterModel(FilterTreeModel filterModel) {
        this.filterModel = filterModel;
        for (FilterNode child : getChildren()) child.setFilterModel(filterModel);
    }

    public final void fireChanged() {
        if (filterModel == null) throw new RuntimeException("Tree model must be set");
        Swing.Exec.later(new Swing() {
            @Override
            public void run() {
                filterModel.refreshNode(FilterNode.this);
            }
        });
    }

    public final void refresh() {
        if (filterModel == null) throw new RuntimeException("Tree model must be set");
        Swing.Exec.later(new Swing() {
            @Override
            public void run() {
                filterModel.refresh();
            }
        });
    }

    public final void filter(String patternString) {
        List<Pattern> patterns = createPatternsFromString(patternString);
        setPassesFilter(patterns.size() > 1);
        filter(patterns);
    }

    private void filter(List<Pattern> patterns) {
        boolean found = false;
        for (Pattern pattern : patterns) {
            if (pattern.matcher(getStringToFilter()).find()) found = true;
        }
        boolean passes = patterns.size() == 1 ? found : !found;
        if (passes != passesFilter) {
            setPassesFilter(passes);
            if (passes) {
                setAllAncestorsPassed();
            }
        }
        else {
            for (FilterNode sub : getChildren()) sub.filter(patterns);
        }
    }

    public void setPassesFilter(boolean passesFilter) {
        this.passesFilter = passesFilter;
        for (FilterNode sub : getChildren()) sub.setPassesFilter(passesFilter);
    }

    public boolean passesFilter() {
        return passesFilter;
    }

    public boolean isHighlighted() {
        return highlighted;
    }

    public void setHighlighted() {
        if (!highlighted) {
            this.highlighted = true;
            fireChanged();
        }
    }

    public void clearHighlighted() {
        if (highlighted) {
            highlighted = false;
            fireChanged();
        }
        for (FilterNode child : getChildren()) child.clearHighlighted();
    }

    public abstract boolean isLeaf();

    public abstract Object getParent();

    public abstract List<? extends FilterNode> getChildren();

    public abstract String getStringToFilter();

    public abstract boolean isAttr();

    public static FilterNode createMessageNode(String message) {
        return new MessageNode(message);
    }

    private void setAllAncestorsPassed() {
        for (FilterNode ancestor = (FilterNode) this.getParent(); ancestor != null; ancestor = (FilterNode) ancestor.getParent()) {
            ancestor.passesFilter = true;
        }
    }

    private List<Pattern> createPatternsFromString(String patternString) {
        List<Pattern> patterns = new ArrayList<Pattern>();
        for (String part : patternString.split(" *, *")) {
            patterns.add(Pattern.compile(part, Pattern.CASE_INSENSITIVE));
        }
        return patterns;
    }



    private static class MessageNode extends FilterNode {
        private String message;

        private MessageNode(String message) {
            this.message = message;
        }

        @Override
        public boolean isLeaf() {
            return true;
        }

        @Override
        public Object getParent() {
            return null;
        }

        @Override
        public List<? extends FilterNode> getChildren() {
            return new ArrayList<FilterNode>();
        }

        @Override
        public String getStringToFilter() {
            return message;
        }

        @Override
        public boolean isAttr() {
            return false;
        }

        @Override
        public String toString() {
            return message;
        }
    }
}
