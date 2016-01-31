package za.org.grassroot.webapp;

import com.github.springtestdbunit.bean.DatabaseConfigBean;
import com.github.springtestdbunit.bean.DatabaseDataSourceConnectionFactoryBean;

import javax.sql.DataSource;

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
