package eu.delving.sip.base;

import javax.swing.SwingUtilities;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handle execution of jobs, as well as managing whether it happens in the swing thread or not
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class Exec {
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static void swing(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            throw new RuntimeException("Call to Swing thread must be made from a Worker thread");
        }
        SwingUtilities.invokeLater(runnable);
    }

    public static void swingLater(Runnable runnable) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new RuntimeException("Call to Swing thread for later must be made from the Swing thread");
        }
        SwingUtilities.invokeLater(runnable);
    }

    public static void swingAny(Runnable runnable) {
        SwingUtilities.invokeLater(runnable);
    }

    public static void work(Runnable runnable) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new RuntimeException("Call to Worker thread must be made from Swing");
        }
        executor.execute(runnable);
    }
}
