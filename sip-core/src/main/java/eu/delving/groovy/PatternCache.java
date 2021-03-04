package eu.delving.groovy;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class PatternCache {

    private static Map<String, Pattern> patternCache = Collections.synchronizedMap(new HashMap<String, Pattern>(200));

    public static Pattern getPattern(String regex) {
        Pattern pattern = patternCache.get(regex);
        if (pattern == null) {
            pattern = Pattern.compile(regex);
            patternCache.put(regex, pattern);
        }
        return pattern;
    }
}
