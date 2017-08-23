package za.org.grassroot.webapp;

import com.ryantenney.metrics.spring.config.annotation.EnableMetrics;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import za.org.grassroot.core.GrassrootApplication;


@Configuration
@ComponentScan
@EnableAutoConfiguration
@EnableMetrics(proxyTargetClass = true)
public class Application {

    public static void main(String[] args) throws Exception {
         new GrassrootApplication(GrassrootWebApplicationConfig.class).run(args);
    }

}
