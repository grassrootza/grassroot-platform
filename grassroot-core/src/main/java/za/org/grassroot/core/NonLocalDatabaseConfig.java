package za.org.grassroot.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

@Configuration
@EnableTransactionManagement @Slf4j
@Profile(value = {GrassrootApplicationProfiles.STAGING, GrassrootApplicationProfiles.PRODUCTION})
public class NonLocalDatabaseConfig {

    @Bean
    public DataSource dataSource() {
        log.info("Running with STAGING profile");
        DataSourceBuilder dataSourceBuilder = DataSourceBuilder.create();
        dataSourceBuilder.url(System.getenv("DATABASE_URL"));
        return dataSourceBuilder.build();
    }

}
