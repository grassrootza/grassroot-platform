package za.org.grassroot.integration.config;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.jpa.convert.threeten.Jsr310JpaConverters;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.xml.Jaxb2RootElementHttpMessageConverter;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestTemplate;

/**
 * Created by paballo on 2016/04/14.
 */

@Configuration
@ComponentScan("za.org.grassroot")
@EntityScan(basePackageClasses = {GrassrootIntegrationConfig.class, Jsr310JpaConverters.class})
@EnableAutoConfiguration
@EnableJpaRepositories
@EnableAsync
@PropertySource(value = "${grassroot.integration.properties}", ignoreResourceNotFound = true) // ignoring else tests fail ...
public class GrassrootIntegrationConfig {

    private static final int DEFAULT_MAX_TOTAL_CONNECTIONS = 100;

    private static final int DEFAULT_MAX_CONNECTIONS_PER_ROUTE = 5;

    private static final int DEFAULT_READ_TIMEOUT_MILLISECONDS = (10 * 1000);

    @Bean
    public ClientHttpRequestFactory httpRequestFactory() {
        return new HttpComponentsClientHttpRequestFactory(httpClient());
    }

    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate(httpRequestFactory());
        restTemplate.getMessageConverters().add(new Jaxb2RootElementHttpMessageConverter());
        return new RestTemplate(httpRequestFactory());
    }

    @Bean
    public CloseableHttpClient httpClient() {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(DEFAULT_MAX_TOTAL_CONNECTIONS);
        connectionManager.setDefaultMaxPerRoute(DEFAULT_MAX_CONNECTIONS_PER_ROUTE);
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(DEFAULT_READ_TIMEOUT_MILLISECONDS)
                .build();

        return HttpClientBuilder.create()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(config)
                .build();
    }

}



