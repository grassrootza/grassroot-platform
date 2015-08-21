package za.org.grassroot.webapp;

import javax.sql.DataSource;

import com.github.springtestdbunit.bean.DatabaseConfigBean;
import com.github.springtestdbunit.bean.DatabaseDataSourceConnectionFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//@Configuration
public class DbUnitConfig {

   // @Bean
    public DatabaseDataSourceConnectionFactoryBean dbUnitDatabaseConnection(DataSource dataSource) {
        final DatabaseConfigBean databaseConfig = new DatabaseConfigBean() {{
            setCaseSensitiveTableNames(true);
            setQualifiedTableNames(true);
            setEscapePattern("\"");

        }};
        return new DatabaseDataSourceConnectionFactoryBean(dataSource) {{
            setDatabaseConfig(databaseConfig);
        }};
    }
}
