package za.org.grassroot.core;

import org.springframework.boot.autoconfigure.jdbc.DataSourceBuilder;
import org.springframework.cloud.Cloud;
import org.springframework.cloud.CloudFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author Lesetse Kimwaga
 */
@Configuration
@Profile(GrassRootApplicationProfiles.PRODUCTION)
public class ProductionDatabaseConfig extends  DatabaseConfig {


    @Override
    public DataSource dataSource() {

        DataSourceBuilder dataSourceBuilder = DataSourceBuilder.create();
        dataSourceBuilder.url(System.getenv("DATABASE_URL"));
        return dataSourceBuilder.build();
    }




}
