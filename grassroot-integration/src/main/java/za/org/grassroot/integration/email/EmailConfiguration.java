package za.org.grassroot.integration.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * Created by paballo on 2016/06/28.
 */

@Configuration
public class EmailConfiguration {

   // @Value("${email.host}")
    private String host;

  //  @Value("${email.port}")
    private Integer port;

   /* @Bean
    public JavaMailSender javaMailSender() {

        JavaMailSenderImpl javaMailSender = new JavaMailSenderImpl();
     ////   javaMailSender.setHost(host);
        javaMailSender.setPort(port);
      //  javaMailSender.setUsername(System.getenv("email_user"));
     //   javaMailSender.setPassword(System.getenv("email_password"));
        javaMailSender.setJavaMailProperties(getMailProperties());

        return javaMailSender;
    }

    private Properties getMailProperties() {
        Properties properties = new Properties();
        properties.setProperty("mail.transport.protocol", "smtp");
        properties.setProperty("mail.smtp.auth", "false");
        properties.setProperty("mail.smtp.starttls.enable", "false");
        properties.setProperty("mail.debug", "false");
        return properties;
    }*/
}




