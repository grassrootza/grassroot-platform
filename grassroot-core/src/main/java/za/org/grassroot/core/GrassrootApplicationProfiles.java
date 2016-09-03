package za.org.grassroot.core;

/**
 * @author Lesetse Kimwaga
 */
public final class GrassrootApplicationProfiles {


    /**
     *
     */
    public final static String TEST = "test";


    /**
     *
     * The default profile for the Meeting Organizer Application.
     * Indicates that the application is running locally is expected
     * to find resources such as Data Sources in-memory.
     */
    public  final static String INMEMORY = "default";


    /**
     *
     * The Local Postgresql profile for the Meeting Organizer Application.
     * Indicates that the application is running against local postgresql database.
     */
    public  final static String LOCAL_PG = "localpg";

    public final static String STAGING = "staging";

    public  final static String PRODUCTION = "production";
}
