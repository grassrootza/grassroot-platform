package za.org.grassroot.meeting_organizer;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * @author Lesetse Kimwaga
 */

@EnableAutoConfiguration
@ComponentScan(basePackages = { "za.org.grassroot.meeting_organizer" })
@Configuration
public class TestContextConfiguration {
}
