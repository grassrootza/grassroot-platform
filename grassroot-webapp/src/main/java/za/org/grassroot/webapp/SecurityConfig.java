package za.org.grassroot.webapp;

import com.google.api.client.http.HttpMethods;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import za.org.grassroot.webapp.interceptor.JwtAuthenticationFilter;

import java.util.Arrays;
import java.util.List;

@EnableWebSecurity @Slf4j
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/v2/api/news/**",
            "/v2/api/activity/**",
            "/v2/api/donate/**",
            "/v2/api/payment/**",
            "/v2/api/image/**",
            "/v2/api/group/search",
            "/v2/api/group/fetch/flyer",
            "/v2/api/group/outside/join/**",
            "/v2/api/language/test/**",
            "/v2/api/jwt/public/credentials",
            "/v2/api/user/profile/image/view/**",
            "/v2/api/inbound/respond/**", // both of these use a complex token-matching to auth, without needing full user, for various UX reasons
            "/v2/api/inbound/unsubscribe/**"
    };

    private static final String[] AUTH_ENDPOINTS = {
            "/v2/api/auth/**"
    };

    private static final String[] DEV_ENDPOINTS = {
            "/v2/api-docs",
            "/swagger-ui.html"
    };

    private final Environment environment;

    @Autowired
    public SecurityConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationTokenFilter() {
        return new JwtAuthenticationFilter();
    }

    @Bean
    public FilterRegistrationBean registration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean registration = new FilterRegistrationBean(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Configuration
    @Order(1)
    public class ApiWebSecurityConfigurationAdapter extends WebSecurityConfigurerAdapter {
        @Override
        protected void configure(HttpSecurity http) throws Exception {
            log.info("Setting up API security configuration");
            http.antMatcher("/v2/api/**")
                .csrf().disable()
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .addFilterBefore(jwtAuthenticationTokenFilter(), UsernamePasswordAuthenticationFilter.class)
                .authorizeRequests()
                .antMatchers(HttpMethods.OPTIONS, "/v2/**").permitAll()
                    .antMatchers(PUBLIC_ENDPOINTS).permitAll()
                    .antMatchers(AUTH_ENDPOINTS).permitAll();
        }

        @Override
        public void configure(WebSecurity web) {
            log.info("API JWT security excluding paths");
            web.ignoring()
                    .antMatchers(HttpMethod.OPTIONS)
                    .antMatchers("/api/**") // since this is Android
                    .antMatchers(PUBLIC_ENDPOINTS)
                    .antMatchers(AUTH_ENDPOINTS);

            if (!environment.acceptsProfiles("production"))
                web.ignoring().antMatchers(DEV_ENDPOINTS);
        }
    }

    @Configuration
    @Order(2)
    public class USSDSecurityConfigurationAdapter extends WebSecurityConfigurerAdapter {
        @Override
        protected void configure(HttpSecurity http) throws Exception {
            log.info("Setting up USSD configuration");
            http
                    .antMatcher("/ussd/**")
                    .authorizeRequests()
                        .antMatchers("/**").access(assembleUssdGatewayAccessString())
                    .anyRequest().authenticated();
        }
    }

    private String assembleUssdGatewayAccessString() {
        if (environment.acceptsProfiles("localpg", "staging")) {
            log.info("Permitting all requests ...");
            return "permitAll";
        }

        final String ussdGateways = environment.getProperty("grassroot.ussd.gateway", "127.0.0.1");
        List<String> gatewayAddresses = Arrays.asList(ussdGateways.split(";"));
        log.debug("Found gateway addresses, split them as : {}", gatewayAddresses);
        String accessStringFormat = "hasIpAddress('%s')";
        String returnString = String.format(accessStringFormat, gatewayAddresses.get(0));
        if (gatewayAddresses.size() > 1) {
            final StringBuilder sb = new StringBuilder(returnString);
            gatewayAddresses.stream().skip(1)
                    .forEach(ip -> sb.append(" or " + String.format(accessStringFormat, ip)));
            returnString = sb.toString();
        }
        log.info("assembled USSD gateway access control : {}", returnString);
        return returnString;
    }
}
