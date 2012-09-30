import junit.framework.TestCase;

/**
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 3/13/12 9:50 AM
 */
public class MappingCategoryTest extends TestCase {

    // conversion website => http://www.uwgb.edu/dutchs/UsefulData/ConvertUTMNoOZ.HTM
    // examples UTM
    // 258602.1675	6648773.532 33
    // with zone 32 you will get into the North Sea
    // https://www.ibm.com/developerworks/java/library/j-coordconvert/

    public void testConvertUTM33toLatLong() {
        assertEquals("59.907116155083855,10.68295344097448", MappingCategory.utm33AsLatLngString("258644.154187,6648939.72091"));
    }

    public void testLatLongToUTM33() {
        assertEquals("32 V 258644.154187 6648939.72091", MappingCategory.latLngAsUTM33String("59.907116,10.682953"));
    }

    public void testRoundTrip() {
        String latLong = "59.907116155083855,10.68295344097448";
        Double easting = 258644.154;
        Double northing = 6648939.72;
        Double lat = 59.907116;
        Double lng = 4.682953;
        final double[] doubles = new UtmCoordinateConversion().utm2LatLon("33 V " + easting + " " + northing);
        assertEquals(lat, doubles[0]);
//        assertEquals(easting + "", new UtmCoordinateConversion().latLon2UTM(lat, lng));
    }
}
