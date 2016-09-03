package za.org.grassroot.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

/**
 * @author Lesetse Kimwaga
 */
@Configuration
@Profile(GrassrootApplicationProfiles.INMEMORY)
public class StandaloneDatabaseConfig extends DatabaseConfig {

    private Logger log = LoggerFactory.getLogger(StandaloneDatabaseConfig.class);


    @Override
    public DataSource dataSource() {
        log.info("Running with DEFAULT H2 database profile");

        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                //.addScript("classpath:db/insert_permissions_seed_data.sql")
                .ignoreFailedDrops(true)
                .build();
    }

    @Bean
    public DataSourceInitializer dataSourceInitializer(DataSource dataSource) {
        ResourceDatabasePopulator databasePopulator = new ResourceDatabasePopulator();


        databasePopulator.addScript(new ClassPathResource("db/insert_permissions_seed_data.sql"));

        //ResourceDatabasePopulator databaseCleaner = new ResourceDatabasePopulator();
        //databaseCleaner.addScript(new ClassPathResource("db/drop_member_table.sql"));
        //databaseCleaner.setIgnoreFailedDrops(true);

        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        initializer.setDatabasePopulator(databasePopulator);
        //initializer.setDatabaseCleaner(databaseCleaner);

        return initializer;
    }


}
