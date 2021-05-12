package eu.delving.sip.cli;

import eu.delving.groovy.GroovyCodeResource;
import eu.delving.sip.files.Storage;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.Feedback;
import eu.delving.sip.model.SipModel;
import eu.delving.sip.model.SipProperties;

import javax.swing.*;

public class CLISipModel extends SipModel {
    public CLISipModel(JDesktopPane desktop, Storage storage, GroovyCodeResource groovyCodeResource, Feedback feedback, SipProperties sipProperties) throws StorageException {
        super(desktop, storage, groovyCodeResource, feedback, sipProperties);
    }
}
