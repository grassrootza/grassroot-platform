package za.org.grassroot.services.integration;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Created by paballo on 2016/04/12.
 */
@Configuration
@EnableAutoConfiguration(exclude = ThymeleafAutoConfiguration.class)
@ComponentScan(basePackages = { "za.org.grassroot" })
public class TestContextConfig {

}