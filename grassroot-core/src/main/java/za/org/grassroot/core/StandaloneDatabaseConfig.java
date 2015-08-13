package za.org.grassroot.core;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

/**
 * @author Lesetse Kimwaga
 */
@Configuration
@Profile(GrassRootApplicationProfiles.STANDALONE)
public class StandaloneDatabaseConfig extends DatabaseConfig {

    @Override
    public DataSource dataSource() {
        org.apache.tomcat.jdbc.pool.DataSource dataSource = new org.apache.tomcat.jdbc.pool.DataSource();

        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:sagan;MODE=PostgreSQL;DB_CLOSE_ON_EXIT=FALSE");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setValidationQuery("SELECT 1");

        configureDataSource(dataSource);
        return dataSource;
    }
}
