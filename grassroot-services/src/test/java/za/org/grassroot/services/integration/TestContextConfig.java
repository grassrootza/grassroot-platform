package za.org.grassroot.services.integration;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * Created by paballo on 2016/04/12.
 */
@Configuration
@EnableAutoConfiguration
@ComponentScan(basePackages = { "za.org.grassroot" })
@PropertySource("/application.properties")
public class TestContextConfig {

}