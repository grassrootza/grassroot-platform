package za.org.grassroot.webapp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * @author Lesetse Kimwaga
 */
@Configuration
//@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@Order(SecurityProperties.ACCESS_OVERRIDE_ORDER)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void configure(WebSecurity web) throws Exception {
        web.ignoring()
                .antMatchers("/static/**")
                .antMatchers("/public/**")
                .antMatchers("/i18n/**")
                .antMatchers("/api/**")
                .antMatchers("/ussd/**")
                .antMatchers("/web/**")
                .antMatchers("/test/**")
                .antMatchers("/console/**")
                .antMatchers("/404")
                .antMatchers("/500");

    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
            http
                .formLogin()
                    .defaultSuccessUrl("/home")
                    .loginPage("/login")
                    .permitAll()
                    .and()
                    .logout()
                    .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                    .logoutSuccessUrl("/")
                    .permitAll()
                    .and()

                        .authorizeRequests()
                        .antMatchers("/signup").permitAll()
                        .antMatchers("/accounts/recovery").permitAll()
                        .antMatchers("/accounts/recovery/success").permitAll()
                        .antMatchers("/grass-root-verification/*").permitAll()
                        .antMatchers("/home").fullyAuthenticated()
                        .anyRequest()
                        .fullyAuthenticated();


    }

    @Override
    public void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth
                .userDetailsService(userDetailsService)
                .passwordEncoder(passwordEncoder);
    }

}
