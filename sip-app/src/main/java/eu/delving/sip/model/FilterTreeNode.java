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

package eu.delving.sip.model;

import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Handle the filtering of tree nodes, both source and target
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public abstract class FilterTreeNode implements TreeNode {

    private boolean passesFilter = true;

    public final void filter(String patternString) {
        List<Pattern> patterns = createPatternsFromString(patternString);
        setPassesFilter(patterns.size() > 1);
        filter(patterns);
    }

    private void filter(List<Pattern> patterns) {
        boolean found = false;
        for (Pattern pattern : patterns) {
            if (pattern.matcher(getStringToFilter()).find()) {
                found = true;
            }
        }
        boolean passes = patterns.size() == 1 ? found : !found;
        if (passes != passesFilter) {
            setPassesFilter(passes);
            if (passes) {
                for (FilterTreeNode ancestor = (FilterTreeNode) this.getParent(); ancestor != null; ancestor = (FilterTreeNode) ancestor.getParent()) {
                    ancestor.passesFilter = true;
                }
            }
        }
        for (FilterTreeNode sub : getChildren()) sub.filter(patterns);
    }

    private List<Pattern> createPatternsFromString(String patternString) {
        List<Pattern> patterns = new ArrayList<Pattern>();
        for (String part : patternString.split(" *, *")) {
            patterns.add(Pattern.compile(part, Pattern.CASE_INSENSITIVE));
        }
        return patterns;
    }

    public final void setPassesFilter(boolean passesFilter) {
        this.passesFilter = passesFilter;
        for (FilterTreeNode sub : getChildren()) sub.setPassesFilter(passesFilter);
    }

    public final boolean passesFilter() {
        return passesFilter;
    }

    public abstract List<? extends FilterTreeNode> getChildren();

    public abstract String getStringToFilter();

}
