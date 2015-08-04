package za.org.grassroot.meeting_organizer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;

import static java.lang.String.format;
import static org.springframework.util.StringUtils.arrayToCommaDelimitedString;
import  static za.org.grassroot.meeting_organizer.MeetingOrganizerApplicationProfiles.*;

/**
 *
 * Enforces Profile Semantics
 * @author Lesetse Kimwaga
 */
public class MeetingOrganizerApplication extends SpringApplication {

    private static final Log logger = LogFactory.getLog(MeetingOrganizerApplication.class);

    public MeetingOrganizerApplication(Class<?> configClass) {
        super(configClass);
    }


    /**
     * Enforce activation of profiles defined in {@link MeetingOrganizerApplicationProfiles}.
     */
    @Override
    protected void configureProfiles(ConfigurableEnvironment environment, String[] args) {
        super.configureProfiles(environment, args);

        boolean standaloneProfileActive = environment.acceptsProfiles(STANDALONE);
        boolean cloudHirokuProfileActive = environment.acceptsProfiles(CLOUDHIROKU);

        if (standaloneProfileActive && cloudHirokuProfileActive) {
            throw new IllegalStateException(format("Only one of the following profiles may be specified: [%s]",
                    arrayToCommaDelimitedString(new String[] { CLOUDHIROKU, STANDALONE })));
        }

        if (standaloneProfileActive || cloudHirokuProfileActive) {
            logger.info(format("Activating '%s' profile because one of '%s' or '%s' profiles have been specified.",
                    STANDALONE, CLOUDHIROKU));
            //environment.addActiveProfile(CLOUDHIROKU);
        }
        else {
            logger.info("The default 'standalone' profile is active because no other profiles have been specified.");
            environment.addActiveProfile(STANDALONE);
        }
//        else {
//            throw new IllegalStateException(format("Unknown profile(s) specified: [%s]. Valid profiles are: [%s]",
//                    arrayToCommaDelimitedString(environment.getActiveProfiles()),
//                    arrayToCommaDelimitedString(new String[] {
//                            arrayToCommaDelimitedString(environment.getDefaultProfiles()), STANDALONE, CLOUDHIROKU })));
//        }

    }
}
