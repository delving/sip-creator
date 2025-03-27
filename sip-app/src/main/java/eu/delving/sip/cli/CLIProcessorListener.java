/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

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
