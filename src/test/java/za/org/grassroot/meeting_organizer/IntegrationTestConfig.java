package za.org.grassroot.meeting_organizer;

import javax.sql.DataSource;

import com.github.springtestdbunit.bean.DatabaseConfigBean;
import com.github.springtestdbunit.bean.DatabaseDataSourceConnectionFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IntegrationTestConfig {

    @Bean
    public DatabaseDataSourceConnectionFactoryBean dbUnitDatabaseConnection(DataSource dataSource, final DatabaseConfigBean dbUnitDatabaseConfig) {

        return new DatabaseDataSourceConnectionFactoryBean(dataSource) {{
            setDatabaseConfig(dbUnitDatabaseConfig);
        }};
    }

    @Bean
    public DatabaseConfigBean dbUnitDatabaseConfig() {

        return new DatabaseConfigBean(){{
            setCaseSensitiveTableNames(true);
            setQualifiedTableNames(true);
            setEscapePattern("\"");
        }};
    }
}
