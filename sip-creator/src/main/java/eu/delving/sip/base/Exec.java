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

package eu.delving.sip.base;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
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

    public static void swingWait(Runnable runnable) {
        try {
            SwingUtilities.invokeAndWait(runnable);
        }
        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static void work(Runnable runnable) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new RuntimeException("Call to Worker thread must be made from Swing");
        }
        executor.execute(runnable);
    }

    public static void workLater(Runnable runnable) {
        executor.execute(runnable);
    }

    public static void checkSwing() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new RuntimeException("Must be Swing thread");
        }
    }
}
