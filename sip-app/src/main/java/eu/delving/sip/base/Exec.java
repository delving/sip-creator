/*
 * Copyright 2011, 2012 Delving BV
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

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handle execution of jobs, as well as managing whether it happens in the swing thread or not
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class Exec {
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static void soon(Swing swing) {
        checkWork();
        SwingUtilities.invokeLater(swing);
    }

    public static void swingLater(Runnable runnable) {
        checkSwing();
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
        checkSwing();
        executor.execute(runnable);
    }

    public static void workLater(Runnable runnable) {
        executor.execute(runnable);
    }

    public static void checkSwing() {
        if (!SwingUtilities.isEventDispatchThread()) throw new RuntimeException("Must be Swing thread");
    }

    public static void checkWork() {
        if (SwingUtilities.isEventDispatchThread()) throw new RuntimeException("Must be Worker thread");
    }
}
