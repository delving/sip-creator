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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class FilenameExtractorTest {

    @ParameterizedTest
    @MethodSource("providePathsAndExpectedResults")
    void shouldExtractBaseNameFromVariousPaths(String input, String expected) {
        assertEquals(expected, FilenameExtractor.extractBaseName(input));
    }

    private static Stream<Arguments> providePathsAndExpectedResults() {
        return Stream.of(
                Arguments.of("edm_5.2.6_record-definition.xml", "edm_5.2.6"),
                Arguments.of("/home/user/files/edm_5.2.6_record-definition.xml", "edm_5.2.6"),
                Arguments.of("/var/data/edm_5.2.6_record-definition.xml", "edm_5.2.6"),
                Arguments.of("./relative/path/edm_5.2.6_record-definition.xml", "edm_5.2.6"),
                Arguments.of("C:\\Windows\\Path\\edm_5.2.6_record-definition.xml", "edm_5.2.6"),
                Arguments.of("different_name_record-definition.xml", "different_name"),
                Arguments.of("file_without_suffix.txt", "file_without_suffix.txt"),
                Arguments.of("just_a_filename", "just_a_filename"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldHandleNullAndEmptyInputs(String input) {
        assertEquals("", FilenameExtractor.extractBaseName(input));
    }

    @Test
    void shouldHandleFileWithMultipleUnderscores() {
        String input = "edm_5.2.6_test_record-definition.xml";
        assertEquals("edm_5.2.6_test", FilenameExtractor.extractBaseName(input));
    }

    @Test
    void shouldHandleFileWithDots() {
        String input = "edm.5.2.6_record-definition.xml";
        assertEquals("edm.5.2.6", FilenameExtractor.extractBaseName(input));
    }

    @Test
    void shouldHandlePathWithSpaces() {
        String input = "/home/user/my files/edm_5.2.6_record-definition.xml";
        assertEquals("edm_5.2.6", FilenameExtractor.extractBaseName(input));
    }

    @Test
    void shouldHandlePathWithSpecialCharacters() {
        String input = "/home/user/特殊字符/edm_5.2.6_record-definition.xml";
        assertEquals("edm_5.2.6", FilenameExtractor.extractBaseName(input));
    }
}
