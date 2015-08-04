package za.org.grassroot.meeting_organizer;

import org.springframework.boot.autoconfigure.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author Lesetse Kimwaga
 */
@Configuration
@Profile(MeetingOrganizerApplicationProfiles.CLOUDHIROKU)
public class HirokuDatabaseConfig extends  DatabaseConfig {

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
        String dbUrl = "jdbc:postgresql://" + dbUri.getHost() + dbUri.getPath();

        DataSourceBuilder dataSourceBuilder = DataSourceBuilder.create();
        dataSourceBuilder.url(dbUrl);
        dataSourceBuilder.username(username);
        dataSourceBuilder.password(password);

        return dataSourceBuilder.build();
    }
}
