package za.org.grassroot;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.orm.jpa.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * @author Lesetse Kimwaga
 */

@EnableAutoConfiguration
@Configuration
@ComponentScan("za.org.grassroot")
@EntityScan
@EnableJpaRepositories
public class GrassRootServicesConfig {
}
