package eu.delving.sip.cli;

import eu.delving.sip.xml.FileProcessor;

public class CLIProcessorListener implements FileProcessor.Listener {
    @Override
    public void failed(FileProcessor fileProcessor) {
        System.out.println("Failed!");
    }

    @Override
    public void aborted(FileProcessor fileProcessor) {
        System.out.println("Aborted!");
    }

    @Override
    public void succeeded(FileProcessor fileProcessor) {
        System.out.println("Success!");
    }
}
