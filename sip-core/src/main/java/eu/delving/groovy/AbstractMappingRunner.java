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

package eu.delving.groovy;

import eu.delving.metadata.RecDefTree;
import eu.delving.metadata.RecMapping;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class AbstractMappingRunner implements MappingRunner{

    protected RecMapping recMapping;
    protected String code;

    public AbstractMappingRunner(RecMapping recMapping, String code) {
        this.recMapping = recMapping;
        this.code = code;
    }

    @Override
    public RecDefTree getRecDefTree() {
        return recMapping.getRecDefTree();
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public abstract Node runMapping(MetadataRecord metadataRecord) throws MappingException;



}
