package za.org.grassroot.core;

import org.springframework.boot.autoconfigure.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

/**
 * @author Lesetse Kimwaga
 */
@Configuration
@Profile(GrassRootApplicationProfiles.STAGING)
public class StagingDatabaseConfig extends DatabaseConfig {


    @Override
    public DataSource dataSource() {

        DataSourceBuilder dataSourceBuilder = DataSourceBuilder.create();
        dataSourceBuilder.url(System.getenv("DATABASE_URL"));
        return dataSourceBuilder.build();
    }




}
