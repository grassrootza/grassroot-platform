package za.org.grassroot.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

        org.apache.tomcat.jdbc.pool.DataSource dataSource = new org.apache.tomcat.jdbc.pool.DataSource();

        if (env.getProperty("db.driver") != null && !env.getProperty("db.driver").trim().equals("")) {
            dbDriver = env.getProperty("db.driver");
        } else {
            dbDriver = "org.postgresql.Driver";
        }

        if (env.getProperty("db.url") != null && !env.getProperty("db.url").trim().equals("")) {
            dbUrl = env.getProperty("db.url");
        } else {
            dbUrl = "jdbc:postgresql://localhost:5432/grassroot";
        }

        if (env.getProperty("db.username") != null && !env.getProperty("db.username").trim().equals("")) {
            dbUsername = env.getProperty("db.username");
        } else {
            dbUsername = "grassroot";
        }

        if (env.getProperty("db.password") != null && !env.getProperty("db.password").trim().equals("")) {
            dbPassword = env.getProperty("db.password");
        } else {
            dbPassword = "verylongpassword";
        }

        dataSource.setDriverClassName(dbDriver);
        dataSource.setUrl(dbUrl);
        dataSource.setUsername(dbUsername);
        dataSource.setPassword(dbPassword);
        dataSource.setValidationQuery("SELECT 1");

        configureDataSource(dataSource);
        return dataSource;
    }
}
