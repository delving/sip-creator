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

import eu.delving.metadata.FieldMapping;
import eu.delving.metadata.MappingModel;
import eu.delving.metadata.RecordMapping;
import eu.delving.sip.base.Exec;
import eu.delving.sip.files.StorageException;

import javax.swing.AbstractListModel;
import java.util.List;

/**
 * A list model for showing the contents of the validation file
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class ReportFileModel extends AbstractListModel implements MappingModel.Listener {
    private SipModel sipModel;
    private List<String> lines;
    private RecordMapping recordMapping;

    public ReportFileModel(SipModel sipModel) {
        this.sipModel = sipModel;
    }

    @Override
    public int getSize() {
        return lines == null ? 0 : lines.size();
    }

    @Override
    public Object getElementAt(int i) {
        return lines.get(i);
    }

    public void refresh() {
        int size = getSize();
        if (size > 0) {
            lines = null;
            Exec.swing(new Runnable() {
                @Override
                public void run() {
                    fireIntervalRemoved(this, 0, getSize());
                }
            });
        }
        try {
            if (recordMapping != null) {
                final List<String> freshLines = sipModel.getDataSetModel().getDataSet().getReport(recordMapping);
                if (freshLines != null) {
                    Exec.swing(new Runnable() {
                        @Override
                        public void run() {
                            lines = freshLines;
                            fireIntervalAdded(ReportFileModel.this, 0, getSize());
                        }
                    });

                }
            }
        }
        catch (StorageException e) {
            sipModel.getFeedback().alert("Validation Report", e);
        }
    }

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
    public void recordMappingChanged(RecordMapping recordMapping) {
        this.recordMapping = recordMapping;
        refresh();
    }

    @Override
    public void recordMappingSelected(RecordMapping recordMapping) {
        recordMappingChanged(recordMapping);
    }

    public void kick() {
        recordMappingChanged(sipModel.getMappingModel().getRecordMapping()); // to fire it off
    }
}