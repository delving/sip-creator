package eu.delving.sip.cli;

import eu.delving.sip.model.Feedback;

import java.util.Arrays;
import java.util.Map;

public class CLIFeedback implements Feedback {

    @Override
    public void info(String message) {
        System.out.println("info: " + message);
    }

    @Override
    public void alert(String message) {
        System.err.println("alert: " + message);
    }

    @Override
    public void alert(String message, Throwable throwable) {
        System.err.println("alert: " + message);
        System.err.println(throwable.getMessage());
        throwable.printStackTrace(System.err);
    }

    @Override
    public String ask(String question) {
        throw new IllegalStateException();
    }

    @Override
    public String ask(String question, String defaultValue) {
        throw new IllegalStateException();
    }

    @Override
    public boolean confirm(String title, String message) {
        throw new IllegalStateException();
    }

    @Override
    public boolean form(String title, Object... components) {
        System.out.println("form: title=" + title + ", components=" + Arrays.toString(components));
        return false;
    }

    @Override
    public String getHubPassword() {
        throw new IllegalStateException();
    }

    @Override
    public boolean getNarthexCredentials(Map<String, String> fields) {
        throw new IllegalStateException();
    }
}
