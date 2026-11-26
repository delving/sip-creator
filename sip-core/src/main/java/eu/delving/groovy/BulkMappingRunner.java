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
import groovy.lang.Binding;
import groovy.lang.Closure;
import groovy.lang.Script;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.jsr223.GroovyScriptEngineImpl;
import org.codehaus.groovy.runtime.GroovyCategorySupport;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;

import javax.script.ScriptException;
import javax.script.SimpleBindings;

/**
 * Optimized implementation for high-throughput batch processing of mappings.
 * Pre-compiles the script for efficiency and provides minimal overhead.
 *
 * <p>The script class is compiled once and each record runs on a new
 * instance of it with its own {@link Binding}. It deliberately does not go
 * through {@code CompiledScript.eval}: Groovy's JSR-223 eval gives every
 * script instance its own metaclass, and while a class has per-instance
 * metaclasses Groovy's invokedynamic call sites cannot cache, so every call
 * in the mapping was relinked on every record, all threads contending on
 * the JDK's method-handle caches.
 */
public class BulkMappingRunner implements MappingRunner {
    private static final Logger LOG = LoggerFactory.getLogger(BulkMappingRunner.class);

    private final Class<?> scriptClass;
    private final RecMapping recMapping;
    private final String generatedCode;

    /**
     * Creates a new BulkMappingRunner with pre-compiled script for efficiency.
     *
     * @param recMapping    The mapping configuration to be applied
     * @param generatedCode The Groovy code to be executed against each record
     * @throws MappingException if script compilation fails
     */
    public BulkMappingRunner(RecMapping recMapping, String generatedCode) throws MappingException {
        this(recMapping, generatedCode, false);
    }

    /**
     * Creates a new BulkMappingRunner, optionally compiling on an isolated
     * engine. Use {@code isolated} for runners that will be cached long-term:
     * the compiled script then pins only its own classloader, so evicting the
     * runner frees its classes. Non-isolated compiles use the shared engine
     * and count toward its periodic Metaspace reset.
     *
     * @param recMapping    The mapping configuration to be applied
     * @param generatedCode The Groovy code to be executed against each record
     * @param isolated      compile on a private engine instead of the shared one
     * @throws MappingException if script compilation fails
     */
    public BulkMappingRunner(RecMapping recMapping, String generatedCode, boolean isolated) throws MappingException {
        this.recMapping = recMapping;
        this.generatedCode = generatedCode;

        try {
            if (isolated) {
                this.scriptClass = compile(EngineHolder.createIsolatedEngine(), generatedCode);
                EngineHolder.notifyIsolatedCompilation();
            } else {
                this.scriptClass = compile(EngineHolder.getInstance(), generatedCode);
                EngineHolder.notifyCompilation();
            }
        } catch (ScriptException e) {
            LOG.error("Failed to compile mapping script: {}", e.getMessage());
            throw new MappingException(
                    MappingException.ErrorType.COMPILATION,
                    "Failed to compile mapping script",
                    e);
        }
    }

    /**
     * Compiles on the engine's class loader, as {@code engine.compile} does,
     * and wraps a compilation failure the same way.
     */
    private static Class<?> compile(GroovyScriptEngineImpl engine, String code) throws ScriptException {
        Class<?> compiled;
        try {
            compiled = engine.getClassLoader().parseClass(code);
        } catch (CompilationFailedException e) {
            throw new ScriptException(e);
        }
        if (!Script.class.isAssignableFrom(compiled)) {
            throw new ScriptException("Mapping code compiled to a class, not a script: " + compiled.getName());
        }
        return compiled;
    }

    /**
     * Runs the script on a fresh instance bound to {@code bindings}. Any
     * exception is wrapped in a ScriptException, as JSR-223 eval does, so
     * callers see the same exception chain.
     */
    private Object evaluate(SimpleBindings bindings) throws ScriptException {
        try {
            Script script = InvokerHelper.createScript(scriptClass, new Binding(bindings));
            return script.run();
        } catch (Exception e) {
            throw new ScriptException(e);
        }
    }

    /**
     * Runs {@code body} with MappingCategory in scope on the calling thread,
     * for a runner whose code was generated with
     * {@code CodeGenerator.withCategoryScope(false)}; every
     * {@link #runMapping} inside {@code body} then relies on it.
     *
     * <p>Entering and leaving a category scope invalidates every Groovy indy
     * call site in the process. Generated code that enters it per record
     * keeps all mapping threads relinking their call sites; entering it once
     * per thread lets them stay linked.
     *
     * <p>Any other Groovy code run inside {@code body} also sees the category,
     * so keep it to mapping work.
     */
    public void runInMappingCategory(Runnable body) {
        Class<?> category;
        try {
            category = scriptClass.getClassLoader().loadClass("MappingCategory");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("MappingCategory is not on the mapping class loader", e);
        }
        GroovyCategorySupport.use(category, new Closure<Void>(this) {
            @SuppressWarnings("unused") // called by Groovy
            public Void doCall() {
                body.run();
                return null;
            }
        });
    }

    @Override
    public RecDefTree getRecDefTree() {
        return recMapping.getRecDefTree();
    }

    @Override
    public String getCode() {
        return generatedCode;
    }

    @Override
    public Node runMapping(MetadataRecord record) throws MappingException {
        LOG.trace("Running mapping for record {}", record);

        try {
            SimpleBindings bindings = Utils.bindingsFor(
                    recMapping.getFacts(),
                    recMapping.getRecDefTree().getRecDef(),
                    record.getRootNode(),
                    recMapping.getRecDefTree().getRecDef().valueOptLookup);

            Object result = evaluate(bindings);
            return Utils.stripEmptyElements(result);

        } catch (ScriptException e) {
            // Special handling for explicitly discarded records
            if (e.getCause() instanceof DiscardRecordException) {
                throw (DiscardRecordException) e.getCause();
            }

            // Handle other execution errors
            String errorMessage = String.format(
                    "Failed to process record (id=%s, number=%s)",
                    record.getId(),
                    record.getRecordNumber());

            LOG.error(errorMessage, e);
            throw new MappingException(
                    MappingException.ErrorType.EXECUTION,
                    errorMessage,
                    e);
        } catch (RuntimeException e) {
            // Handle unexpected runtime errors
            String errorMessage = String.format(
                    "Unexpected error processing record (id=%s, number=%s)",
                    record.getId(),
                    record.getRecordNumber());

            LOG.error(errorMessage, e);
            throw new MappingException(
                    MappingException.ErrorType.EXECUTION,
                    errorMessage,
                    e);
        }
    }

    /**
     * Gets the underlying RecMapping configuration.
     * 
     * @return The RecMapping being used by this runner
     */
    public RecMapping getRecMapping() {
        return recMapping;
    }
}
