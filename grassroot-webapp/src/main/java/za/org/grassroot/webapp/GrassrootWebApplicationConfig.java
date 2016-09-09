package za.org.grassroot.webapp;

import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.embedded.EmbeddedServletContainerFactory;
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory;
import org.springframework.boot.orm.jpa.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
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

    // private ApplicationContext applicationContext;

    private static final Logger logger = LoggerFactory.getLogger(GrassrootWebApplicationConfig.class);

    @Autowired
    Environment environment;

    /* Spring boot does not support Thymeleaf 3 yet, hence removing this

    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    private ITemplateResolver templateResolver() {
        SpringResourceTemplateResolver resolver = new SpringResourceTemplateResolver();
        resolver.setApplicationContext(applicationContext);
        resolver.setPrefix("classpath:/templates/");
        resolver.setTemplateMode(TemplateMode.HTML);
        return resolver;
    }

    private TemplateEngine templateEngine() {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(templateResolver());
        return engine;
    }

    @Bean
    public ViewResolver viewResolver() {
        ThymeleafViewResolver resolver = new ThymeleafViewResolver();
        resolver.setTemplateEngine(templateEngine());
        return resolver;
    }*/

    @Bean
    @Profile({ "staging", "production", "localpg" })
    public EmbeddedServletContainerFactory servletContainer() {

        TomcatEmbeddedServletContainerFactory tomcat = new TomcatEmbeddedServletContainerFactory(){
            @Override
            protected void postProcessContext(Context context) {
                if (environment.acceptsProfiles("staging", "production")) {
                    SecurityConstraint securityConstraint = new SecurityConstraint();
                    securityConstraint.setUserConstraint("CONFIDENTIAL");
                    SecurityCollection collection = new SecurityCollection();
                    collection.addPattern("/*");
                    securityConstraint.addCollection(collection);
                    context.addConstraint(securityConstraint);
                }
            }
        };

        if (environment.acceptsProfiles("staging", "production")) {
            Integer httpPort = environment.getRequiredProperty("grassroot.http.port", Integer.class);
            Integer httpsPort = environment.getRequiredProperty("grassroot.https.port", Integer.class);
            logger.info("starting up tomcat, http port obtained = {}, and https obtained = {}", httpPort, httpsPort);
            Connector nonSSLConnector = createNonSSLConnectorWithRedirect(httpPort, httpsPort);
            tomcat.addAdditionalTomcatConnectors(nonSSLConnector);
        }

        return tomcat;
    }

    private Connector createNonSSLConnectorWithRedirect(int httpPort, int httpsPort) {
        logger.info("setting non SSL port inside connector");
        Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
        connector.setPort(httpPort);
        connector.setRedirectPort(httpsPort);
        return connector;
    }

}
