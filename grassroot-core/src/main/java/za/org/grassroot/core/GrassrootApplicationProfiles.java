package za.org.grassroot.core;

/**
 * @author Lesetse Kimwaga
 */
public final class GrassrootApplicationProfiles {

    /**
     *
     * The default profile. Indicates that the application is running locally is expected
     * to find resources such as Data Sources in-memory.
     */
    public  final static String INMEMORY = "default";


    /*
     * Local Postgresql profile. Indicates that the application is running against local postgresql database.
     */
    public  final static String LOCAL_PG = "localpg";

    public final static String STAGING = "staging";

    public  final static String PRODUCTION = "production";
}
