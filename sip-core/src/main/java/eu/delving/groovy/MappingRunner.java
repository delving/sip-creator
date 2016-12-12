package eu.delving.groovy;

import java.util.Map;
import java.util.Optional;

public interface MappingRunner {
    /**
     * Transforms xml by applying a (Groovy) script to it.
     *
     * @param record            the xml of the input-record to be mapped (transformed)
     * @param scriptCode        the groovy script to be applied to <code>record</code>
     * @param additionalContext additional variables to be bound to the script execution context.
     * @return the resulting XML fragment
     * @throws IllegalArgumentException when the scriptCode can not be compiled by the underlying engine.
     * @throws IllegalArgumentException when any of the objects in <code>additionalContext</code>vars are not of stock JDK classes.
     */
    Optional<String> transform(String record, String scriptCode, Map<String, ?> additionalContext) throws IllegalArgumentException;

}
