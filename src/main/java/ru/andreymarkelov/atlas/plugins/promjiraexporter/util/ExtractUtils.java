package ru.andreymarkelov.atlas.plugins.promjiraexporter.util;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.isBlank;

public class ExtractUtils {
    private ExtractUtils() {
    }

    public static List<String> extractSegments(String path) {
        if (isBlank(path)) {
            return emptyList();
        }

        List<String> segments = new ArrayList<>();
        boolean b = true;
        int i = 0;
        do {
            i = path.indexOf("/", i + 1);
            if (i < 0) {
                b = false;
            } else {
                segments.add(path.substring(0, i));
            }
        } while (b);
        segments.add(path);

        String lastSegment = segments.remove(segments.size() - 1);
        int queryIndex = lastSegment.indexOf("?");
        segments.add((queryIndex > 0) ? lastSegment.substring(0, queryIndex) : lastSegment);

        return segments;
    }
}
