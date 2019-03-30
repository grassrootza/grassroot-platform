package za.org.grassroot.webapp.util;

public final class DebugProfileUtil {

    public static long currentHeapSizeMb() {
        return Runtime.getRuntime().totalMemory() / (1024 * 1024);
    }

    private static long maxHeapSizeMb() {
        return Runtime.getRuntime().maxMemory() / (1024 * 1024);
    }

    private static long freeHeapSizeMb() {
        return Runtime.getRuntime().freeMemory() / (1024 * 1024);
    }

    public static String memoryStats() {
        return "Current heap: " + currentHeapSizeMb() + "mb and max : " + maxHeapSizeMb() + "mb, leaving free: " + freeHeapSizeMb() + "mb";
    }
}
