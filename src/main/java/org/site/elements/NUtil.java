package org.site.elements;

import java.time.ZonedDateTime;

public class NUtil {
    static ZonedDateTime dateFromString(String str) {
        return ZonedDateTime.parse(str);
    }
}
