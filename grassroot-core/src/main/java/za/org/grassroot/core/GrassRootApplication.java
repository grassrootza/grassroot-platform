package za.org.grassroot.core;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;

import static java.lang.String.format;
import static org.springframework.util.StringUtils.arrayToCommaDelimitedString;

/**
 *
 * Enforces Profile Semantics
 * @author Lesetse Kimwaga
 */
public class GrassRootApplication extends SpringApplication {

    private static final Log logger = LogFactory.getLog(GrassRootApplication.class);

    public GrassRootApplication(Class<?> configClass) {
        super(configClass);
    }


    /**
     * Enforce activation of profiles defined in {@link GrassRootApplicationProfiles}.
     */
    @Override
    protected void configureProfiles(ConfigurableEnvironment environment, String[] args) {
        super.configureProfiles(environment, args);

        int numberActive = 0;

        if (environment.acceptsProfiles(GrassRootApplicationProfiles.INMEMORY)) numberActive++;
        if (environment.acceptsProfiles(GrassRootApplicationProfiles.CLOUDHEROKU)) numberActive++;
        if (environment.acceptsProfiles(GrassRootApplicationProfiles.LOCAL_PG)) numberActive++;

        if (numberActive > 1) {
            throw new IllegalStateException(format("Only one of the following profiles may be specified: [%s]",
                    arrayToCommaDelimitedString(new String[] {GrassRootApplicationProfiles.CLOUDHEROKU, GrassRootApplicationProfiles.INMEMORY, GrassRootApplicationProfiles.LOCAL_PG })));
        }

        if (numberActive == 1) {
            logger.info("Activating because one  profiles have been specified.");

        }
        else {
            logger.info("The default 'standalone' profile is active because no other profiles have been specified.");
            environment.addActiveProfile(GrassRootApplicationProfiles.INMEMORY);
        }
    }
}
