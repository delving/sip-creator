/*
 * Copyright 2011, 2012 Delving BV
 *
 *  Licensed under the EUPL, Version 1.0 or? as soon they
 *  will be approved by the European Commission - subsequent
 *  versions of the EUPL (the "Licence");
 *  you may not use this work except in compliance with the
 *  Licence.
 *  You may obtain a copy of the Licence at:
 *
 *  http://ec.europa.eu/idabc/eupl
 *
 *  Unless required by applicable law or agreed to in
 *  writing, software distributed under the Licence is
 *  distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied.
 *  See the Licence for the specific language governing
 *  permissions and limitations under the Licence.
 */

package eu.delving.metadata;

import eu.delving.MappingCompletion;
import eu.delving.MappingEngine;
import eu.delving.MappingResult;
import eu.delving.groovy.*;
import eu.delving.schema.SchemaVersion;
import org.w3c.dom.Node;

import java.io.StringReader;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executor;

/**
 * Wrapping the mapping mechanism so that mappings can be executed independent of the rest of the SIP-Creator.
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class MappingEngineImpl implements MappingEngine {
    private ClassLoader classLoader;
    private Executor executor;
    private RecDefModel recDefModel;
    private MetadataRecordFactory metadataRecordFactory;
    private Map<SchemaVersion, MappingRunner> mappingRunners = new TreeMap<SchemaVersion, MappingRunner>();

    public MappingEngineImpl(ClassLoader classLoader, Executor executor, RecDefModel recDefModel, Map<String, String> namespaces) {
        this.classLoader = classLoader;
        this.executor = executor;
        this.recDefModel = recDefModel;
        this.metadataRecordFactory = new MetadataRecordFactory(namespaces);
    }

    @Override
    public void addMappingRunner(SchemaVersion schemaVersion, String mapping) throws MetadataException {
        RecMapping recMapping = RecMapping.read(new StringReader(mapping), recDefModel);
        GroovyCodeResource groovyCodeResource = new GroovyCodeResource(classLoader);
        mappingRunners.put(schemaVersion, new MappingRunner(groovyCodeResource, recMapping, null, false));
    }

    @Override
    public void mapRecord(int index, String recordId, String recordXML, SchemaVersion[] schemaVersions, MappingCompletion completion) {
        executor.execute(new MappingJob(index, recordId, recordXML, schemaVersions, completion));
    }

    public String toString() {
        if (mappingRunners.isEmpty()) return "No mapping runners";
        if (mappingRunners.size() == 1) return mappingRunners.values().iterator().next().getCode();
        StringBuilder out = new StringBuilder();
        for (Map.Entry<SchemaVersion, MappingRunner> entry : mappingRunners.entrySet()) {
            out.append(entry.getKey()).append('\n');
            out.append(entry.getValue().getCode()).append('\n');
        }
        return out.toString();
    }

    private class MappingJob implements Runnable {
        private XmlSerializer serializer = new XmlSerializer();
        private int index;
        private String recordId;
        private String recordXML;
        private SchemaVersion[] schemaVersions;
        private MappingCompletion completion;

        private MappingJob(int index, String recordId, String recordXML, SchemaVersion[] schemaVersions, MappingCompletion completion) {
            this.index = index;
            this.recordId = recordId;
            this.recordXML = recordXML;
            this.schemaVersions = schemaVersions;
            this.completion = completion;
        }

        @Override
        public void run() {
            try {
                MetadataRecord metadataRecord = metadataRecordFactory.metadataRecordFrom(recordId, recordXML);
                Map<SchemaVersion, MappingResult> resultMap = new TreeMap<SchemaVersion, MappingResult>();
                for (SchemaVersion schemaVersion : schemaVersions) {
                    MappingResult mappingResult;
                    if (schemaVersion.equals(SchemaVersion.RAW)) {
                        Node root = metadataRecordFactory.nodeFromXml(recordId, recordXML);
                        mappingResult = new MappingResultImpl(serializer, root, null);
                    }
                    else {
                        MappingRunner runner = mappingRunners.get(schemaVersion);
                        if (runner == null) {
                            throw new IllegalStateException("No mapping runner found for " + schemaVersion);
                        }
                        Node node = runner.runMapping(metadataRecord);
                        mappingResult = new MappingResultImpl(serializer, node, runner.getRecDefTree()).resolve();
                    }
                    resultMap.put(schemaVersion,mappingResult);
                }
                completion.onSuccess(index, resultMap);
            }
            catch (Throwable t) {
                completion.onFailure(index, t);
            }
        }
    }
}
