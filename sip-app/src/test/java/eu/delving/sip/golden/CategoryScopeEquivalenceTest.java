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

import eu.delving.groovy.BulkMappingRunner;
import eu.delving.groovy.MetadataRecord;
import eu.delving.groovy.XmlSerializer;
import eu.delving.metadata.CodeGenerator;
import eu.delving.metadata.MappingResult;
import eu.delving.metadata.RecDef;
import eu.delving.metadata.RecDefTree;
import eu.delving.metadata.RecMapping;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FileProcessor without XSD validation (its default) runs code generated
 * without its own {@code use(MappingCategory)} block inside a category the
 * engine thread entered once. Every golden case must map to the same bytes
 * that way as with the category-scoped code.
 */
public class CategoryScopeEquivalenceTest {

    private static final Path MAPPINGS = Path.of("..", "lua-poc", "golden", "mappings");

    @Test
    public void categoryPerThreadMatchesCategoryScoped() throws Exception {
        List<Path> cases;
        try (Stream<Path> dirs = Files.list(MAPPINGS)) {
            cases = dirs.filter(d -> Files.exists(d.resolve("mapping.xml"))).sorted().collect(Collectors.toList());
        }
        assertTrue(cases.size() >= 10, "golden cases found: " + cases.size());
        for (Path caseDir : cases) {
            assertArrayEquals(map(caseDir, true), map(caseDir, false), caseDir.getFileName().toString());
        }
    }

    private static byte[] map(Path caseDir, boolean categoryScope) throws Exception {
        RecDef recDef;
        try (InputStream in = Files.newInputStream(caseDir.resolve("recdef.xml"))) {
            recDef = RecDef.read(in);
        }
        RecMapping recMapping;
        try (InputStream in = Files.newInputStream(caseDir.resolve("mapping.xml"))) {
            recMapping = RecMapping.read(in, RecDefTree.create(recDef));
        }
        String code = new CodeGenerator(recMapping).withEditPath(null).withTrace(false)
                .withCategoryScope(categoryScope).toRecordMappingCode();
        BulkMappingRunner runner = new BulkMappingRunner(recMapping, code);
        MetadataRecord record = GoldenCapture.parseRecord(caseDir.resolve("record.xml"));
        AtomicReference<Node> node = new AtomicReference<>();
        if (categoryScope) {
            node.set(runner.runMapping(record));
        } else {
            runner.runInMappingCategory(() -> {
                try {
                    node.set(runner.runMapping(record));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
        MappingResult result = new MappingResult(new XmlSerializer(), record.getId(), node.get(),
                runner.getRecDefTree(), recMapping.getFacts());
        return result.toByteArrayOutputStream(recMapping.getFacts()).toByteArray();
    }
}
