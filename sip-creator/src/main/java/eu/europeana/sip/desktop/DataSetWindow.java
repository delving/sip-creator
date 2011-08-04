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

package eu.europeana.sip.desktop;

import eu.europeana.sip.util.GridBagAdapter;

import javax.swing.*;
import java.awt.*;

/**
 * Contains the following elements:
 *
 * <ul>
 * <li>List of data sets</li>
 * <li>Search component for datasets</li>
 * <li>Change log</li>
 * <ul>
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public class DataSetWindow extends JPanel {

    private static final String TITLE_LABEL = "Data sets";
    private JLabel title = new JLabel(TITLE_LABEL);
    private JList dataSets;

    public DataSetWindow() {
        setLayout(new GridBagLayout());
        buildLayout();
    }

    private void buildLayout() {
        GridBagAdapter gba = new GridBagAdapter();
        gba.reset();
        add(title, gba);
        dataSets = new JList(fetchListModel());
        gba.line();
        add(dataSets, gba);
    }

    private Object[] fetchListModel() {
        return new String[]{"Fries Museum", "Princessehof"};
    }
}
