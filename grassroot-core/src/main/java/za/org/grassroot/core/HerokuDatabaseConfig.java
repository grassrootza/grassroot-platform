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
@Profile(GrassRootApplicationProfiles.CLOUDHEROKU)
public class HerokuDatabaseConfig extends  DatabaseConfig {


    @Bean
    public Cloud cloud() {
        return new CloudFactory().getCloud();
    }


//    @Override
//    public DataSource dataSource() {
//        DataSource dataSource = cloud().getServiceConnector("meeting-organizer-db", DataSource.class, null);
//        Assert.isInstanceOf(org.apache.tomcat.jdbc.pool.DataSource.class, dataSource);
//        configureDataSource((org.apache.tomcat.jdbc.pool.DataSource) dataSource);
//        return dataSource;
//    }

    @Override
    public DataSource dataSource() {

        URI dbUri = null;
        try {
            dbUri = new URI(System.getenv("DATABASE_URL"));
        } catch (URISyntaxException e) {
            throw  new RuntimeException("Could not get Database URL environment variable");
        }

        String username = dbUri.getUserInfo().split(":")[0];
        String password = dbUri.getUserInfo().split(":")[1];
        String dbUrl = "jdbc:postgresql://" + dbUri.getHost() + ":" + dbUri.getPort() + dbUri.getPath();

        DataSourceBuilder dataSourceBuilder = DataSourceBuilder.create();
        dataSourceBuilder.url(dbUrl);
        dataSourceBuilder.username(username);
        dataSourceBuilder.password(password);


        return dataSourceBuilder.build();
    }
}
