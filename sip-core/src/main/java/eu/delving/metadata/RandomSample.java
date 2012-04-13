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

package eu.delving.metadata;

import java.io.Serializable;
import java.util.*;

/**
 * Regardless of what happens, just grab some random values as a backup in case other interesting statistics
 * don't work.  Trying to be clever here to collect a sample that is close to randomly plucked from a stream
 * of values.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class RandomSample implements Serializable {
    private static final int RANDOM_SAMPLE_SIZE = 300;
    private static final int SAMPLE_SIZE = 15;
    private static final int MAX_VALUE_LENGTH = 40;
    private Set<String> values = new TreeSet<String>();

    public void recordValue(String value) {
        if (values.size() < RANDOM_SAMPLE_SIZE || Math.random() > 0.1) {
            values.add(value);
        }
        if (values.size() > RANDOM_SAMPLE_SIZE * 2) {
            Iterator<String> walk = values.iterator();
            while (walk.hasNext()) {
                walk.next();
                if (Math.random() > 0.5) {
                    walk.remove();
                }
            }
        }
    }

    public List<String> getFirstValues() {
        List<String> first = new ArrayList<String>(SAMPLE_SIZE);
        Iterator<String> it = values.iterator();
        for (int walk = 0; walk < SAMPLE_SIZE; walk++) if (it.hasNext()) {
            String value = it.next();
            first.add(value.length() > MAX_VALUE_LENGTH ? value.substring(0, MAX_VALUE_LENGTH) + "..." : value);
        }
        return first;
    }

    public Set<String> getValues() {
        return values;
    }


    public int getSize() {
        return values.size();
    }
}