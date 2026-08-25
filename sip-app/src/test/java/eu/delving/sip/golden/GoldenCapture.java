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

package eu.delving.sip.golden;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.w3c.dom.Node;

import eu.delving.groovy.BulkMappingRunner;
import eu.delving.groovy.MetadataRecord;
import eu.delving.groovy.XmlSerializer;
import eu.delving.metadata.CodeGenerator;
import eu.delving.metadata.MappingResult;
import eu.delving.metadata.RecDef;
import eu.delving.metadata.RecDefTree;
import eu.delving.metadata.RecMapping;
import eu.delving.sip.xml.MetadataParser;

/**
 * Golden-record capture harness for the Lua mapping-engine feasibility
 * project (see {@code .superpowers/sdd/2026-08-25-lua-mapping-engine-deepdive/}).
 *
 * <p>Reads a case directory ({@code <caseDir>/mapping.xml},
 * {@code <caseDir>/recdef.xml}, {@code <caseDir>/record.xml}), runs the
 * record through the real Groovy mapping engine (the same
 * {@link CodeGenerator} + {@link BulkMappingRunner} path used by
 * {@code FileProcessor} for bulk indexing), and writes the resulting RDF/XML
 * to the given output file. The output is a golden file: a candidate Lua
 * engine's output for the same case is diffed against it (Task 5's
 * verifier).
 *
 * <p>{@code record.xml} holds a single {@code <pocket id="...">...</pocket>}
 * fragment extracted from the dataset's compressed source, unmodified except
 * for whitespace trimming — this harness wraps it in a {@code <pockets>}
 * root and feeds it to {@link MetadataParser}, exactly as production parses
 * dataset source files.
 */
public class GoldenCapture {

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("usage: GoldenCapture <caseDir> <outFile>");
            System.exit(1);
            return;
        }
        Path outFile = capture(Paths.get(args[0]), Paths.get(args[1]));
        System.out.println("wrote " + outFile);
    }

    /**
     * Maps {@code caseDir/record.xml} through {@code caseDir/mapping.xml}
     * (validated against {@code caseDir/recdef.xml}) and writes the
     * resulting RDF/XML to {@code outFile}.
     *
     * @param caseDir directory containing {@code mapping.xml}, {@code recdef.xml}, {@code record.xml}
     * @param outFile destination file for the captured RDF/XML; parent directories are created as needed
     * @return {@code outFile}, for convenience chaining
     */
    public static Path capture(Path caseDir, Path outFile) throws Exception {
        RecDef recDef;
        try (InputStream in = Files.newInputStream(caseDir.resolve("recdef.xml"))) {
            recDef = RecDef.read(in);
        }
        RecDefTree recDefTree = RecDefTree.create(recDef);

        RecMapping recMapping;
        try (InputStream in = Files.newInputStream(caseDir.resolve("mapping.xml"))) {
            recMapping = RecMapping.read(in, recDefTree);
        }

        String code = new CodeGenerator(recMapping).withTrace(false).toRecordMappingCode();
        BulkMappingRunner mappingRunner = new BulkMappingRunner(recMapping, code);

        MetadataRecord record = parseRecord(caseDir.resolve("record.xml"));

        Node mappedNode = mappingRunner.runMapping(record);

        XmlSerializer serializer = new XmlSerializer();
        MappingResult result = new MappingResult(
                serializer, record.getId(), mappedNode, mappingRunner.getRecDefTree(), recMapping.getFacts());
        String xml = result.toXml();

        if (outFile.getParent() != null) {
            Files.createDirectories(outFile.getParent());
        }
        Files.writeString(outFile, xml, StandardCharsets.UTF_8);
        return outFile;
    }

    /**
     * Parses a {@code record.xml} fixture (a bare {@code <pocket id="...">...</pocket>}
     * fragment) the same way production parses dataset source files: wrap it
     * in a {@code <pockets>} root and hand it to {@link MetadataParser},
     * which is what supplies the {@code @id} that becomes
     * {@link MetadataRecord#getId()} and the {@code GroovyNode} tree that
     * backs {@code _input} in generated mapping code.
     */
    private static MetadataRecord parseRecord(Path recordFile) throws Exception {
        String pocketXml = Files.readString(recordFile, StandardCharsets.UTF_8).trim();
        String wrapped = "<pockets>" + pocketXml + "</pockets>";
        InputStream in = new ByteArrayInputStream(wrapped.getBytes(StandardCharsets.UTF_8));
        MetadataParser parser = new MetadataParser(in, 1);
        try {
            MetadataRecord record = parser.nextRecord();
            if (record == null) {
                throw new IOException("no record parsed from " + recordFile);
            }
            return record;
        } finally {
            parser.close();
        }
    }
}
