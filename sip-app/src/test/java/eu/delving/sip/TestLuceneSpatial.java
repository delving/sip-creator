/*
 * Copyright 2011, 2012 Delving BV
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

package eu.delving.sip;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.spatial.DistanceUtils;
import org.apache.lucene.spatial.tier.InvalidGeoException;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

/**
 *
 */

public class TestLuceneSpatial {

    @Test
    public void validateList() throws IOException {
        URL url = getClass().getResource("/geo/latlong.txt");
        List<String> lines = FileUtils.readLines(new File(url.getFile()));
        for (String latlong : lines) {
            try {
                DistanceUtils.parseLatitudeLongitude(latlong);
            }
            catch (InvalidGeoException e) {
                Assert.assertEquals("Unexpected exception", "624.3020535333326,76.0872380450499", latlong);
            }
        }
    }
}
