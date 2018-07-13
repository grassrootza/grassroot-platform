package za.org.grassroot.core;

/**
 * @author Lesetse Kimwaga
 */
public final class GrassrootApplicationProfiles {

    public final static String INMEMORY = "default"; // default, indicates application is running locally & expects resources in-memory
    public final static String LOCAL_PG = "localpg"; // local postgres, used for all dev work
    public final static String STAGING = "staging";
    public final static String PRODUCTION = "production";
}
