package za.org.grassroot.webapp;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Created by luke on 2015/07/23.
 */
@Configuration
@ComponentScan("za.org.grassroot")
@EntityScan
@EnableAutoConfiguration
@EnableJpaRepositories
public class GrassrootWebApplicationConfig {

}
