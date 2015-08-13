package za.org.grassroot.webapp;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import za.org.grassroot.core.GrassRootApplication;


@Configuration
@ComponentScan
@EnableAutoConfiguration
public class Application {

    public static void main(String[] args) throws Exception {
         new GrassRootApplication(GrassRootWebApplicationConfig.class).run(args);
    }

}
