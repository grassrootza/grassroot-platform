package za.org.grassroot;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * @author Lesetse Kimwaga
 */
@EnableAutoConfiguration
@ComponentScan(basePackages = { "za.org.grassroot" })
@Configuration
public class TestContextConfiguration {
}
