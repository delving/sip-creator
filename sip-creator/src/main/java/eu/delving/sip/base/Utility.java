/*
 * Copyright 2010 DELVING BV
 *
 *  Licensed under the EUPL, Version 1.0 or? as soon they
 *  will be approved by the European Commission - subsequent
 *  versions of the EUPL (the "Licence");
 *  you may not use this work except in compliance with the
 *  Licence.
 *  You may obtain a copy of the Licence at:
 *
 *  http://ec.europa.eu/idabc/eupl
 *
 *  Unless required by applicable law or agreed to in
 *  writing, software distributed under the Licence is
 *  distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied.
 *  See the Licence for the specific language governing
 *  permissions and limitations under the Licence.
 */

package eu.delving.sip.base;

import javax.jnlp.BasicService;
import javax.jnlp.ServiceManager;
import javax.jnlp.UnavailableServiceException;
import javax.swing.JTextArea;
import javax.swing.Spring;
import javax.swing.SpringLayout;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import java.awt.Component;
import java.awt.Container;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Help with SpringLayout forms
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class Utility {

    private static SpringLayout.Constraints getConstraintsForCell(int row, int col, Container parent, int cols) {
        SpringLayout layout = (SpringLayout) parent.getLayout();
        Component c = parent.getComponent(row * cols + col);
        return layout.getConstraints(c);
    }

    public static void makeCompactGrid(Container parent, int rows, int cols, int initialX, int initialY, int xPad, int yPad) {
        SpringLayout layout = (SpringLayout) parent.getLayout();
        //Align all cells in each column and make them the same width.
        Spring x = Spring.constant(initialX);
        for (int c = 0; c < cols; c++) {
            Spring width = Spring.constant(0);
            for (int r = 0; r < rows; r++) {
                width = Spring.max(width, getConstraintsForCell(r, c, parent, cols).getWidth());
            }
            for (int r = 0; r < rows; r++) {
                SpringLayout.Constraints constraints = getConstraintsForCell(r, c, parent, cols);
                constraints.setX(x);
                constraints.setWidth(width);
            }
            x = Spring.sum(x, Spring.sum(width, Spring.constant(xPad)));
        }

        //Align all cells in each row and make them the same height.
        Spring y = Spring.constant(initialY);
        for (int r = 0; r < rows; r++) {
            Spring height = Spring.constant(0);
            for (int c = 0; c < cols; c++) {
                height = Spring.max(height, getConstraintsForCell(r, c, parent, cols).getHeight());
            }
            for (int c = 0; c < cols; c++) {
                SpringLayout.Constraints constraints = getConstraintsForCell(r, c, parent, cols);
                constraints.setY(y);
                constraints.setHeight(height);
            }
            y = Spring.sum(y, Spring.sum(height, Spring.constant(yPad)));
        }

        //Set the parent's size.
        SpringLayout.Constraints pCons = layout.getConstraints(parent);
        pCons.setConstraint(SpringLayout.EAST, x);
    }

    public static void attachUrlLauncher(JTextArea area) {
        new URLLauncher(area);
    }

    public static class URLLauncher implements CaretListener {

        private JTextArea outputArea;

        private URLLauncher(JTextArea outputArea) {
            this.outputArea = outputArea;
            outputArea.addCaretListener(this);
        }

        @Override
        public void caretUpdate(CaretEvent e) {
            int dot = e.getDot();
            int mark = e.getMark();
            if (dot != mark) {
                String text = outputArea.getText();
                int min = Math.min(dot, mark);
                int max = Math.min(text.length() - 1, Math.max(dot, mark));
                String urlString = text.substring(min, max);
                if (min > 1 && text.charAt(min - 1) == '>' && max < text.length() && text.charAt(max) == '<') {
                    if (validUrl(urlString)) {
                        showURL(urlString);
                    }
                    else {
                        outputArea.select(min, min);
                    }
                }
                else {
                    while (min > 1 && text.charAt(min - 1) != '>') {
                        min--;
                    }
                    while (max < text.length() - 1 && text.charAt(max + 1) != '<') {
                        max++;
                    }
                    if (validUrl(text.substring(min, max + 1))) {
                        outputArea.select(min, max + 1);
                    }
                    else {
                        outputArea.select(min, min);
                    }
                }
            }
        }

        private boolean validUrl(String urlString) {
            try {
                if (urlString.contains(">") || urlString.contains("<")) {
                    return false;
                }
                new URL(urlString);
                return true;
            }
            catch (MalformedURLException e1) {
                return false;
            }
        }

        boolean showURL(String urlString) {
            try {
                urlString = urlString.replaceAll("&amp;", "&");
                URL url = new URL(urlString);
                BasicService bs = (BasicService) ServiceManager.lookup("javax.jnlp.BasicService");
                return bs.showDocument(url);
            }
            catch (UnavailableServiceException ue) {
                System.out.println("Wanted to launch " + urlString);
                return false;
            }
            catch (MalformedURLException e1) {
                return false;
            }
        }
    }

    public static URL getCodebase() {
        try {
            BasicService bs = (BasicService) ServiceManager.lookup("javax.jnlp.BasicService");
            return bs.getCodeBase();
        }
        catch (UnavailableServiceException ue) {
            return null;
        }
    }
}
