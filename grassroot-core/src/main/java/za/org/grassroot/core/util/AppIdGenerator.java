package za.org.grassroot.core.util;

import java.util.UUID;

public class AppIdGenerator {
    private AppIdGenerator() {
        // utility
    }

    public static String generateId() {
        return UUID.randomUUID().toString();
    }
}
