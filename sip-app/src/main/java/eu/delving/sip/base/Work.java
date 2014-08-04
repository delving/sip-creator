package eu.delving.sip.base;

import eu.delving.sip.files.DataSet;

import static eu.delving.sip.base.Work.Kind.DATA_SET;
import static eu.delving.sip.base.Work.Kind.DATA_SET_PREFIX;
import static eu.delving.sip.base.Work.Kind.NETWORK;
import static eu.delving.sip.base.Work.Kind.NETWORK_DATA_SET;
import static eu.delving.sip.base.Work.Kind.SILENT;

/**
 * A runnable that will be executed in the worker thread
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
        SELECT_ANOTHER_MAPPING(SILENT),
        DUPLICATE_ELEMENT(SILENT),

        READ_FRAME_ARRANGEMENTS(SILENT),

        COMPILE_NODE_MAPPING(DATA_SET_PREFIX),
        COMPILE_FUNCTION(DATA_SET_PREFIX),

        SET_DATASET(DATA_SET_PREFIX),
        SAVE_MAPPING(DATA_SET_PREFIX),
        REVERT_MAPPING(DATA_SET_PREFIX),

        DELETE_SOURCE(DATA_SET),
        CHECK_STATE(DATA_SET),
        SEEK_RESET(DATA_SET),
        SAVE_HINTS(DATA_SET),

        IMPORT_SOURCE(DATA_SET),
        IMPORT_MEDIA(DATA_SET),
        PARSE_ANALYZE(DATA_SET),
        CONVERT_SOURCE(DATA_SET),
        SCAN_RECORDS(DATA_SET),
        PROCESS(DATA_SET_PREFIX),
        LOAD_REPORT(DATA_SET_PREFIX),
        CHECK_LINK(DATA_SET_PREFIX),
        LOAD_LINKS(DATA_SET_PREFIX),
        SAVE_LINKS(DATA_SET_PREFIX),
        GATHER_LINK_STATS(DATA_SET_PREFIX),
        GATHER_PRESENCE_STATS(DATA_SET_PREFIX),
        RELOAD_MAPPING(DATA_SET_PREFIX),
        DELETE_CACHES(DATA_SET_PREFIX),

        FETCH_LIST(NETWORK),
        FETCH_FACTS_DEF(NETWORK),
        FETCH_HELP(NETWORK),

        HARVEST(NETWORK_DATA_SET),
        UNLOCK(NETWORK_DATA_SET),
        DOWNLOAD(NETWORK_DATA_SET),
        UPLOAD(NETWORK_DATA_SET);

        private Kind kind;

        private Job(Kind kind) {
            this.kind = kind;
        }

        public Kind getKind() {
            return kind;
        }
    }

    Job getJob();

    public interface DataSetWork extends Work {
        DataSet getDataSet();
    }

    public interface DataSetPrefixWork extends DataSetWork {
        String getPrefix();
    }

    public interface LongTermWork extends Work {
        void setProgressListener(ProgressListener progressListener);
    }
}
