package za.org.grassroot.core.util;

import java.util.UUID;

public class UIDGenerator {
    private UIDGenerator() {
        // utility
    }

    public static String generateId() {
        return UUID.randomUUID().toString();
    }
}
