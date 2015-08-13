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

        boolean standaloneProfileActive = environment.acceptsProfiles(GrassRootApplicationProfiles.STANDALONE);
        boolean cloudHerokuProfileActive = environment.acceptsProfiles(GrassRootApplicationProfiles.CLOUDHEROKU);

        if (standaloneProfileActive && cloudHerokuProfileActive) {
            throw new IllegalStateException(format("Only one of the following profiles may be specified: [%s]",
                    arrayToCommaDelimitedString(new String[] {GrassRootApplicationProfiles.CLOUDHEROKU, GrassRootApplicationProfiles.STANDALONE })));
        }

        if (standaloneProfileActive || cloudHerokuProfileActive) {
            logger.info("Activating because one  profiles have been specified.");

        }
        else {
            logger.info("The default 'standalone' profile is active because no other profiles have been specified.");
            environment.addActiveProfile(GrassRootApplicationProfiles.STANDALONE);
        }
    }
}
