package eu.delving.sip.base;

/**
 * A runnable that will be executed in the workder thread
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public interface Work extends Runnable {
    public enum Job {

        CLEAR_FACTS_STATS,
        CLEAR_NODE_MAPPING,
        SELECT_NODE_MAPPING,
        REMOVE_NODE_MAPPING,
        SET_OPERATOR,
        SELECT_SOURCE_SET_SOURCE,
        DROP_TRANSFER_HANDLER,
        CREATE_MAPPING,
        FUNCTION_REMOVE,
        FUNCTION_COPY_FROM_LIBRARY,
        FUNCTION_DOC_CHANGE_LISTENER,
        SET_MAPPING_HINTS_FIND_NODES,
        COPY_MAPPING_FROM_HINTS,
        FUNCTION_SELECT,
        NOTIFY_DICTIONARY_CHANGED,
        NOTIFY_FUNCTION_CHANGED,
        SELECT_REC_DEF_SET_TARGET,
        REVERT_NODE_MAPPING,
        MAPPING_DOCUMENT_CHANGED,
        REFRESH_DICTIONARY,
        REMOVE_DICTIONARY,

        CHECK_DATA_SET_STATE,
        SET_DATASET_PREFIX,
        UNLOCK_MAPPING,
        SAVE_HINTS,
        SAVE_MAPPING,
        DELETE_SOURCE_FOR_RECONVERT,
        SEEK_RESET,
        REVERT_MAPPING,
        FIND_INVALID_PREFIXES,

        READ_FRAME_ARRANGEMENTS,

        NODE_MAPPING_COMPILE_RUN,
        FUNCTION_COMPILE_RUN,

        HARVEST_OAI_PMH,
        IMPORT_SOURCE,
        PARSE_ANALYZE,
        CONVERT_SOURCE,
        SCAN_INPUT_RECORDS,
        PROCESS_FILE,

        HTTP_FETCH_DATASET_LIST,
        HTTP_UNLOCK_DATA_SET,
        HTTP_DOWNLOAD_DATASET,
        HTTP_UPLOAD_FILES,
        HTTP_FETCH_HELP_TEXT,

    }

    Job getJob();
}
