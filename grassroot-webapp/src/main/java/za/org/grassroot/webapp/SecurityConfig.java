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

    @Override
    public void configure(WebSecurity web) throws Exception {
        web.ignoring()
                .antMatchers("/static/**")
                .antMatchers("/public/**")
                .antMatchers("/i18n/**")
                .antMatchers("/ussd/**")
                .antMatchers("/web/**")
                .antMatchers("/test/**")
                .antMatchers("/console/**");
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
//        http
//                .authorizeRequests()
//
//                .antMatchers("/public/**", "/static/**").permitAll()
//                .antMatchers("/signin").permitAll()
//                .antMatchers("/ussd").permitAll()
//                .antMatchers("/ussd/**").permitAll()
//                .antMatchers("/web/**").permitAll()
//                .antMatchers("/users/**").hasAuthority("ADMIN")
//                //.antMatchers("/**").hasAnyRole("USER")
//                .anyRequest().hasAnyAuthority("USER")
//                .and()
//                    .formLogin()
//                    .loginPage("/login").permitAll()
//                .and()
//                    .logout()
//                    .logoutUrl("/logout")
//                    .deleteCookies("remember-me")
//                    .logoutSuccessUrl("/signin")
//                .and()
//                    .rememberMe();


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
                        .antMatchers("/home").fullyAuthenticated()
                        .anyRequest()
                        .fullyAuthenticated();


    }

//    @Override
//    public void configure(AuthenticationManagerBuilder auth) throws Exception {
//        auth
//                .userDetailsService(userDetailsService)
//                .passwordEncoder(new BCryptPasswordEncoder());
//    }

    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
        auth
                .inMemoryAuthentication()
                .withUser("user").password("password").roles("USER");


    }
}
