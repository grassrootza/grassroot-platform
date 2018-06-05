package za.org.grassroot.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

/**
 * @author Luke Jordan
 */
@Configuration
@Profile(GrassrootApplicationProfiles.STAGING)
public class StagingDatabaseConfig extends DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(StagingDatabaseConfig.class);

    @Override
    public DataSource dataSource() {
        log.info("Running with STAGING profile");
        DataSourceBuilder dataSourceBuilder = DataSourceBuilder.create();
        dataSourceBuilder.url(System.getenv("DATABASE_URL"));
        return dataSourceBuilder.build();
    }

}
