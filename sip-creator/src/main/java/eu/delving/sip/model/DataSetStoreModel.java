/*
 * Copyright 2011 DELVING BV
 *
 *  Licensed under the EUPL, Version 1.0 or? as soon they
 *  will be approved by the European Commission - subsequent
 *  versions of the EUPL (the "Licence");
 *  you may not use this work except in compliance with the
 *  Licence.
 *  You may obtain a copy of the Licence at:
 *
 *  http://ec.europa.eu/idabc/eupl
 *
 *  Unless required by applicable law or agreed to in
 *  writing, software distributed under the Licence is
 *  distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied.
 *  See the Licence for the specific language governing
 *  permissions and limitations under the Licence.
 */

package eu.delving.sip.model;

import eu.delving.sip.files.FileStore;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An observable hole to put the current data set store
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class DataSetStoreModel {
    private FileStore.DataSetStore dataSetStore;
    private FileStore.StoreState storeState = FileStore.StoreState.EMPTY;

    public boolean hasStore() {
        return dataSetStore != null;
    }

    public FileStore.DataSetStore getStore() {
        if (dataSetStore == null) {
            throw new IllegalStateException("There is no dataset store in the model!");
        }
        return dataSetStore;
    }

    public void setStore(FileStore.DataSetStore dataSetStore) {
        this.dataSetStore = dataSetStore;
        this.storeState = dataSetStore.getState();
        for (Listener listener : listeners) {
            listener.storeSet(dataSetStore);
        }
    }

    public void checkState() { // todo: somebody has to call this when things may have changed!
        FileStore.StoreState actualState = dataSetStore.getState();
        if (actualState != storeState) {
            storeState = actualState;
            for (Listener listener : listeners) {
                listener.storeStateChanged(dataSetStore, actualState);
            }
        }
    }

    private List<Listener> listeners = new CopyOnWriteArrayList<Listener>();

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public interface Listener {
        void storeSet(FileStore.DataSetStore store);
        void storeStateChanged(FileStore.DataSetStore store, FileStore.StoreState storeState);
    }
}
