package za.org.grassroot.services;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@EnableAutoConfiguration(exclude = { ThymeleafAutoConfiguration.class })
@ComponentScan(basePackages = { "za.org.grassroot" })
@Configuration
public class ServicesTestConfig {
}
