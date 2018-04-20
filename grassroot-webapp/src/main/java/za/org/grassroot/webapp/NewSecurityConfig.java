package za.org.grassroot.webapp;

import com.google.api.client.http.HttpMethods;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import za.org.grassroot.webapp.interceptor.JwtAuthenticationFilter;

@Configuration @Order(1)
public class NewSecurityConfig extends WebSecurityConfigurerAdapter {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/v2/api/news/**",
            "/v2/api/activity/**",
            "/v2/api/donate/**",
            "/v2/api/group/search",
            "/v2/api/group/fetch/flyer",
            "/v2/api/group/outside/join/**",
            "/v2/api/language/test/**",
            "/v2/api/jwt/public/credentials",
            "/v2/api/user/profile/image/**"
    };

    private static final String[] AUTH_ENDPOINTS = {
            "/v2/api/auth/**"
    };

    private static final String[] DEV_ENDPOINTS = {
            "/v2/api-docs"
    };

    private final Environment environment;

    @Autowired
    public NewSecurityConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationTokenFilter() throws Exception {
        return new JwtAuthenticationFilter();
    }

    @Bean
    public FilterRegistrationBean registration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean registration = new FilterRegistrationBean(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.antMatcher("/v2/**")
                .csrf().disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    .and()
                .authorizeRequests()
                    .antMatchers(HttpMethods.OPTIONS, "/v2/**").permitAll()
                    .antMatchers(PUBLIC_ENDPOINTS).permitAll()
                    .antMatchers(AUTH_ENDPOINTS).permitAll()
                .anyRequest().authenticated();

        http.addFilterBefore(jwtAuthenticationTokenFilter(), UsernamePasswordAuthenticationFilter.class);
    }

    @Override
    public void configure(WebSecurity web) throws Exception {
        web.ignoring()
                .antMatchers(HttpMethod.OPTIONS)
                .antMatchers(PUBLIC_ENDPOINTS)
                .antMatchers(AUTH_ENDPOINTS);

        if (!environment.acceptsProfiles("production"))
            web.ignoring().antMatchers(DEV_ENDPOINTS);
    }
}
