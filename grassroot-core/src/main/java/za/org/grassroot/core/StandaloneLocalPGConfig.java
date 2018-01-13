package za.org.grassroot.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;


/**
 * @author Lesetse Kimwaga
 */
@Configuration @Slf4j
@Profile(GrassrootApplicationProfiles.LOCAL_PG)
public class StandaloneLocalPGConfig extends DatabaseConfig {

   private final Environment env;

    @Autowired
    public StandaloneLocalPGConfig(Environment env) {
        this.env = env;
    }

    @Override
   public DataSource dataSource() {
       log.info("Running with LOCAL_PG profile");
       DataSourceBuilder dataSourceBuilder = DataSourceBuilder.create();
       dataSourceBuilder.url(env.getProperty("DATABASE_URL"));
       return dataSourceBuilder.build();
   }
}