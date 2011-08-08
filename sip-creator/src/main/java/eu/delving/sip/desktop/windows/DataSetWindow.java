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

package eu.delving.sip.desktop.windows;

import eu.europeana.sip.util.GridBagHelper;

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
public class DataSetWindow extends DesktopWindow {

    private static final String TITLE_LABEL = "Data sets";
    private JLabel title = new JLabel(TITLE_LABEL);
    private JList dataSets;
    private JTextField filter = new JTextField("Filter");
    private JButton select = new JButton("Select");
    private JButton cancel = new JButton("Cancel");

    public DataSetWindow() {
        super(WindowId.DATA_SET);
        setLayout(new GridBagLayout());
        buildLayout();
    }

    private void buildLayout() {
        GridBagHelper gba = new GridBagHelper();
        gba.reset();
        add(title, gba);
        dataSets = new JList(fetchListModel());
        gba.line();
        add(dataSets, gba);
        gba.line();
        add(cancel, gba);
        gba.right();
        add(select, gba);
    }

    private Object[] fetchListModel() {
        return new String[]{"Fries Museum", "Princessehof"};
    }
}
