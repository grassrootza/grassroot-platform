package za.org.grassroot.core;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * @author Lesetse Kimwaga
 */
@Configuration
public class CoreConfig {

    @Bean
    public PasswordEncoder getPasswordEncoder()
    {
        return  new BCryptPasswordEncoder();
    }


}
