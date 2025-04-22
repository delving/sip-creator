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

package eu.delving.metadata;

import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.referencing.operation.projection.MapProjection;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.crs.GeographicCRS;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GeoUtil {

    public static String convertUTM(String line, boolean utmOut) {
        MapProjection.class.getClassLoader().setClassAssertionStatus(MapProjection.class.getName(), false);
        final GeographicCRS geoCRS = DefaultGeographicCRS.WGS84;
        CoordinateReferenceSystem projCRS;
        MathTransform transformGeoToCrs;
        MathTransform transformCrsToGeo;
        double[] dest = new double[2];
        Matcher sridMatcher = Pattern.compile("\\s*SRID=(\\d+);POINT\\((\\d+),(\\d+)\\)").matcher(line);
        Matcher utm33Matcher = Pattern.compile("(\\d+) V (\\d+\\.\\d+|\\d+) *(\\d+\\.\\d+|\\d+)").matcher(line);
        Matcher commaMatcher = Pattern.compile("(\\d+\\.\\d+|\\d+), *(\\d+\\.\\d+|\\d+)").matcher(line);
        Matcher spaceMatcher = Pattern.compile("(\\d+\\.\\d+|\\d+) (\\d+\\.\\d+|\\d+)").matcher(line);
        if (sridMatcher.matches()) {
            int id = Integer.parseInt(sridMatcher.group(1));
            int zone = id % 100;
            double east = Double.parseDouble(sridMatcher.group(2));
            double north = Double.parseDouble(sridMatcher.group(3));
            if (utmOut) {
                return line;
            } else {
                try {
                    transformCrsToGeo = CRS.findMathTransform(CRS.decode("EPSG:326" + zone), geoCRS, false);
                } catch (FactoryException e) {
                    throw new RuntimeException(e);
                }
                double[] src = new double[] { east, north };
                try {
                    transformCrsToGeo.transform(src, 0, dest, 0, 1);
                } catch (TransformException e) {
                    throw new RuntimeException(e);
                }
                String out = dest[1] + "," + dest[0];
                return out;
            }
        } else if (utm33Matcher.matches()) {
            int zone = Integer.parseInt(utm33Matcher.group(1));
            double east = Double.parseDouble(utm33Matcher.group(2));
            double north = Double.parseDouble(utm33Matcher.group(3));
            if (utmOut) {
                String out = "SRID=326" + zone + ";POINT(" + east +  "," + north + ")";
                return out;
            } else {
                try {
                    transformCrsToGeo = CRS.findMathTransform(CRS.decode("EPSG:326" + zone), geoCRS, false);
                } catch (FactoryException e) {
                    throw new RuntimeException(e);
                }
                double[] src = new double[] { east, north };
                try {
                    transformCrsToGeo.transform(src, 0, dest, 0, 1);
                } catch (TransformException e) {
                    throw new RuntimeException(e);
                }
                String out = dest[1] + "," + dest[0];
                return out;
            }
        } else if (spaceMatcher.matches()) {
            double east = Double.parseDouble(spaceMatcher.group(1));
            double north = Double.parseDouble(spaceMatcher.group(2));
            if (utmOut) {
                String out = "SRID=32633" + ";POINT(" + east +  "," + north + ")";
                return out;
            } else {
                try {
                    transformCrsToGeo = CRS.findMathTransform(CRS.decode("EPSG:32633"), geoCRS, false);
                } catch (FactoryException e) {
                    throw new RuntimeException(e);
                }
                double[] src = new double[] { east, north };
                try {
                    transformCrsToGeo.transform(src, 0, dest, 0, 1);
                } catch (TransformException e) {
                    throw new RuntimeException(e);
                }
                String out = dest[1] + "," + dest[0];
                return out;
            }
        } else if (commaMatcher.matches()) {
            double latitude = Double.parseDouble(commaMatcher.group(1));
            double longitude = Double.parseDouble(commaMatcher.group(2));
            if (utmOut) {
                try {
                    projCRS = CRS.decode("EPSG:32633");
                    transformGeoToCrs = CRS.findMathTransform(geoCRS, projCRS, false);
                } catch (FactoryException e) {
                    throw new RuntimeException(e);
                }
                double[] src = new double[] { longitude, latitude };
                try {
                    transformGeoToCrs.transform(src, 0, dest, 0, 1);
                } catch (TransformException e) {
                    throw new RuntimeException(e);
                }
                String out = "SRID=32633" + ";POINT(" + dest[0] +  "," + dest[1] + ")";
                return out;
            } else {
                return latitude + "," + longitude;
            }
        } else {
            return "";
        }
    }

}
