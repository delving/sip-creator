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

package eu.delving.metadata;

/**
 * Raised by {@link GroovySnippetToLua} and {@link LuaMappingGenerator} when a
 * mapping uses a construct outside the T1 tier the Lua spike implements.
 *
 * <p>The whole point of this exception is that it is <em>loud and named</em>:
 * the spike's value depends on knowing exactly which constructs a T2/T3 mapping
 * needs, so every refusal carries the construct's name (an AST class name, a
 * {@code MethodCall:<name>}, or a {@code Regex*} classification from
 * {@code docs/specs/mapping-language-core.md} section 9.4). Silently emitting
 * approximate Lua would make golden comparison meaningless.
 */
public class UnsupportedConstructException extends RuntimeException {

    private final String constructName;

    public UnsupportedConstructException(String constructName) {
        this(constructName, null);
    }

    public UnsupportedConstructException(String constructName, String detail) {
        super("not in the T1 tier: " + constructName + (detail == null ? "" : " (" + detail + ")"));
        this.constructName = constructName;
    }

    public String getConstructName() {
        return constructName;
    }
}
