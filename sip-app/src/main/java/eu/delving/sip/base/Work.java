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

package eu.delving.sip.base;

import eu.delving.sip.files.DataSet;

import static eu.delving.sip.base.Work.Kind.DATA_SET;
import static eu.delving.sip.base.Work.Kind.DATA_SET_PREFIX;
import static eu.delving.sip.base.Work.Kind.NETWORK;
import static eu.delving.sip.base.Work.Kind.SILENT;
import static eu.delving.sip.base.Work.Lane.BATCH;
import static eu.delving.sip.base.Work.Lane.NONE;

/**
 * A runnable that will be executed in the worker thread
 *
 *
 */

public interface Work extends Runnable {
    
    public enum Kind {
        SILENT,
        NETWORK,
        NETWORK_DATA_SET,
        DATA_SET,
        DATA_SET_PREFIX
    }
    
    /**
     * Which queue a job waits in. EDIT and BATCH are per dataset: WorkModel runs the jobs of one (dataset, lane)
     * one after the other, and lets an EDIT job overlap a BATCH job because BATCH jobs snapshot their inputs at
     * start. NETWORK is one global queue. NONE runs unqueued, for jobs that touch no shared model.
     */
    public enum Lane {
        EDIT,
        BATCH,
        NETWORK,
        NONE
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

        READ_FRAME_ARRANGEMENTS(SILENT, NONE),

        COMPILE_NODE_MAPPING(DATA_SET_PREFIX),
        COMPILE_FUNCTION(DATA_SET_PREFIX),

        SET_DATASET(DATA_SET),
        SAVE_MAPPING(DATA_SET_PREFIX),
        REVERT_MAPPING(DATA_SET_PREFIX),

        DELETE_SOURCE(DATA_SET),
        CHECK_STATE(DATA_SET),
        SEEK_RESET(DATA_SET),
        SAVE_HINTS(DATA_SET),

        PARSE_ANALYZE(DATA_SET),
        SCAN_RECORDS(DATA_SET),
        PROCESS(DATA_SET_PREFIX, BATCH),
        LOAD_REPORT(DATA_SET_PREFIX, BATCH),
        CHECK_LINK(DATA_SET_PREFIX, BATCH),
        LOAD_LINKS(DATA_SET_PREFIX, BATCH),
        SAVE_LINKS(DATA_SET_PREFIX, BATCH),
        GATHER_LINK_STATS(DATA_SET_PREFIX, BATCH),
        GATHER_PRESENCE_STATS(DATA_SET_PREFIX, BATCH),
        RELOAD_MAPPING(DATA_SET_PREFIX),
        DELETE_CACHES(DATA_SET_PREFIX, BATCH),

        LOGIN(NETWORK),
        FETCH_LIST(NETWORK),
        FETCH_FACTS_DEF(NETWORK),
        FETCH_HELP(NETWORK),

        DOWNLOAD(NETWORK),
        UPLOAD(NETWORK);

        private Kind kind;
        private Lane lane;

        // the default lane: network jobs share the network queue, everything else edits the live models
        private Job(Kind kind) {
            this(kind, kind == NETWORK ? Lane.NETWORK : Lane.EDIT);
        }

        private Job(Kind kind, Lane lane) {
            this.kind = kind;
            this.lane = lane;
        }

        public Kind getKind() {
            return kind;
        }

        public Lane getLane() {
            return lane;
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
