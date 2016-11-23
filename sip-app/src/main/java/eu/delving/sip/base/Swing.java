package eu.delving.sip.base;

import javax.swing.*;

/**
 * A runnable that will be executed in the Swing thread
 *
 *
 */

public interface Swing extends Runnable {

    public static class Exec {
        public static void later(Swing swing) {
            SwingUtilities.invokeLater(swing);
        }
    }
}
