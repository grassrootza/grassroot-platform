package za.org.grassroot.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;


/**
 * @author Lesetse Kimwaga
 */
@Configuration
@Profile(GrassrootApplicationProfiles.LOCAL_PG)
public class StandaloneLocalPGConfig extends DatabaseConfig {

   private static final Logger log = LoggerFactory.getLogger(StandaloneLocalPGConfig.class);

   @Autowired
    private Environment env;

   private String dbDriver;
    private String dbUrl;
    private String dbUsername;
    private String dbPassword;

   @Override
    public DataSource dataSource() {
        log.info("Running with LOCAL_PG profile");
        DataSourceBuilder dataSourceBuilder = DataSourceBuilder.create();
        dataSourceBuilder.url(env.getProperty("DATABASE_URL"));
        return dataSourceBuilder.build();
    }
}