package eu.delving.sip.cli;

import eu.delving.sip.base.CancelException;
import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.model.Feedback;

public class CLIProgressListener implements ProgressListener {

    @Override
    public void setProgressMessage(String message) {
        // ignore unhelpful messages
    }

    @Override
    public void prepareFor(int total) {
       // ignore since total is always 0
    }

    @Override
    public void setProgress(int progress) {
        // ignore updates
    }

    @Override
    public Feedback getFeedback() {
        throw new UnsupportedOperationException();
    }
}
