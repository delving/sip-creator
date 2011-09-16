package eu.delving.metadata;

/**
 * Reduce the number of empty methods.
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public class MappingModelAdapter implements MappingModel.Listener {

    @Override
    public void factChanged() {
    }

    @Override
    public void select(FieldMapping fieldMapping) {
    }

    @Override
    public void fieldMappingChanged() {
    }

    @Override
    public void recordMappingChanged(@Deprecated RecordMapping recordMapping) {
    }

    @Override
    public void recordMappingSelected(RecordMapping recordMapping) {
    }
}
