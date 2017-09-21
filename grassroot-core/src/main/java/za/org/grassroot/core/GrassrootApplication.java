package za.org.grassroot.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;

import static java.lang.String.format;
import static org.springframework.util.StringUtils.arrayToCommaDelimitedString;

import com.ryantenney.metrics.spring.config.annotation.EnableMetrics;

/**
 *
 * Enforces Profile Semantics
 * @author Lesetse Kimwaga
 */
@EnableMetrics(proxyTargetClass = true)
public class GrassrootApplication extends SpringApplication {

    private static final Logger logger = LoggerFactory.getLogger(GrassrootApplication.class);

    public GrassrootApplication(Class<?> configClass) {
        super(configClass);
    }

    /**
     * Enforce activation of profiles defined in {@link GrassrootApplicationProfiles}.
     */
    @Override
    protected void configureProfiles(ConfigurableEnvironment environment, String[] args) {
        super.configureProfiles(environment, args);

        int numberActive = 0;

        if (environment.acceptsProfiles(GrassrootApplicationProfiles.INMEMORY)) numberActive++;
        if (environment.acceptsProfiles(GrassrootApplicationProfiles.LOCAL_PG)) numberActive++;
        if (environment.acceptsProfiles(GrassrootApplicationProfiles.STAGING)) numberActive++;
        if (environment.acceptsProfiles(GrassrootApplicationProfiles.PRODUCTION)) numberActive++;

        if (numberActive > 1) {
            throw new IllegalStateException(format("Only one of the following profiles may be specified: [%s]",
                    arrayToCommaDelimitedString(new String[] {GrassrootApplicationProfiles.PRODUCTION, GrassrootApplicationProfiles.INMEMORY, GrassrootApplicationProfiles.LOCAL_PG })));
        }

        if (numberActive == 1) {
            logger.info("Activating because one profile has been specified.");
        } else {
            logger.info("The default 'standalone' profile is active because no other profiles have been specified.");
            environment.addActiveProfile(GrassrootApplicationProfiles.INMEMORY);
        }
    }
}
