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
import java.util.List;
import java.util.regex.Pattern;

/**
 * Handle the filtering of tree nodes, both source and target
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public interface FilterTreeNode extends TreeNode {

    void filter(Pattern pattern);

    void setPassesFilter(boolean passesFilter);

    boolean passesFilter();

    List<? extends FilterTreeNode> getChildren();
}
