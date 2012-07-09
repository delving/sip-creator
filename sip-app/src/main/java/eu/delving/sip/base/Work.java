package eu.delving.sip.base;

import static eu.delving.sip.base.Work.Kind.*;

/**
 * A runnable that will be executed in the workder thread
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public interface Work extends Runnable {
    
    public enum Kind {
        SILENT,
        NETWORK,
        NETWORK_DATA_SET,
        DATA_SET,
        DATA_SET_PREFIX
    }
    
    public enum Job {

        CLEAR_FACTS_STATS(SILENT),
        CLEAR_NODE_MAPPING(SILENT),
        SELECT_NODE_MAPPING(SILENT),
        REMOVE_NODE_MAPPING(SILENT),
        SET_OPERATOR(SILENT),
        SELECT_SOURCE_SET_SOURCE(SILENT),
        DROP_TRANSFER_HANDLER(SILENT),
        CREATE_MAPPING(SILENT),
        FUNCTION_REMOVE(SILENT),
        FUNCTION_COPY_FROM_LIBRARY(SILENT),
        FUNCTION_DOC_CHANGE_LISTENER(SILENT),
        SET_MAPPING_HINTS_FIND_NODES(SILENT),
        COPY_MAPPING_FROM_HINTS(SILENT),
        FUNCTION_SELECT(SILENT),
        NOTIFY_DICTIONARY_CHANGED(SILENT),
        NOTIFY_FUNCTION_CHANGED(SILENT),
        SELECT_REC_DEF_SET_TARGET(SILENT),
        REVERT_NODE_MAPPING(SILENT),
        MAPPING_DOCUMENT_CHANGED(SILENT),
        REFRESH_DICTIONARY(SILENT),
        REMOVE_DICTIONARY(SILENT),
        UNLOCK_MAPPING(SILENT),
        NODE_MAPPING_COMPILE_RUN(SILENT),
        FUNCTION_COMPILE_RUN(SILENT),

        READ_FRAME_ARRANGEMENTS(SILENT),

        CHECK_DATA_SET_STATE(DATA_SET_PREFIX),
        SET_DATASET_PREFIX(DATA_SET_PREFIX),
        SAVE_HINTS(DATA_SET_PREFIX),
        SAVE_MAPPING(DATA_SET_PREFIX),
        REVERT_MAPPING(DATA_SET_PREFIX),
        FIND_INVALID_PREFIXES(DATA_SET),
        DELETE_SOURCE_FOR_RECONVERT(DATA_SET),
        SEEK_RESET(DATA_SET),

        IMPORT_SOURCE(DATA_SET),
        PARSE_ANALYZE(DATA_SET),
        CONVERT_SOURCE(DATA_SET),
        SCAN_INPUT_RECORDS(DATA_SET),
        PROCESS_FILE(DATA_SET_PREFIX),

        FETCH_DATASET_LIST(NETWORK),
        FETCH_HELP_TEXT(NETWORK),

        HARVEST_OAI_PMH(NETWORK_DATA_SET),
        UNLOCK_DATA_SET(NETWORK_DATA_SET),
        DOWNLOAD_DATASET(NETWORK_DATA_SET),
        UPLOAD_FILES(NETWORK_DATA_SET)
        ;

        private Kind kind;

        private Job(Kind kind) {
            this.kind = kind;
        }

        public Kind getKind() {
            return kind;
        }
    }

    Job getJob();
}
