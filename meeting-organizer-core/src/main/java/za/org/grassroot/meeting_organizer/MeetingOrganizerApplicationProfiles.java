package za.org.grassroot.meeting_organizer;

/**
 * @author Lesetse Kimwaga
 */
public final class MeetingOrganizerApplicationProfiles {


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
    public  final static String STANDALONE = "default";


    /**
     *
     * Profile indicates that the application is running on Hiroku
     * and should expect to find data sources in Hiroku Services
     */
    public final static String CLOUDHIROKU = "hiroku";
}
