package za.org.grassroot.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.orm.jpa.AbstractEntityManagerFactoryBean;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Lesetse Kimwaga
 */
@Configuration
@ComponentScan(basePackages = { "za.org.grassroot.core" })
@EnableJpaRepositories(basePackages = "za.org.grassroot.core.repository")
@Profile(GrassrootApplicationProfiles.INMEMORY)
@EnableTransactionManagement
public class StandaloneDatabaseConfig {

    private Logger log = LoggerFactory.getLogger(StandaloneDatabaseConfig.class);

    @Bean
    public DataSource dataSource() {
        log.info("Running with DEFAULT H2 database profile");
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .ignoreFailedDrops(true)
                .build();
    }

    @Bean
    public PlatformTransactionManager transactionManager() {
        return new JpaTransactionManager(entityManagerFactory().getObject());
    }

    @Bean
    public AbstractEntityManagerFactoryBean entityManagerFactory() {
        Map<String, Object> jpaProperties = new HashMap<>();
        log.info("Setting up entity manager factory, with Hibernate properties ...");

        LocalContainerEntityManagerFactoryBean localContainerEntityManagerFactoryBean = new LocalContainerEntityManagerFactoryBean();
        localContainerEntityManagerFactoryBean.setPackagesToScan("za.org.grassroot.core");
        // localContainerEntityManagerFactoryBean.setJpaPropertyMap(jpaProperties);
        localContainerEntityManagerFactoryBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        localContainerEntityManagerFactoryBean.setDataSource(dataSource());

        return localContainerEntityManagerFactoryBean;
    }

}
