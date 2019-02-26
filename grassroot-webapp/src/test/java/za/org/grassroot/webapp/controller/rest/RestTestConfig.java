package za.org.grassroot.webapp.controller.rest;

import net.sf.ehcache.CacheManager;
import org.springframework.cache.ehcache.EhCacheManagerFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class RestTestConfig {
	@Bean(name = "cacheManager")
	public EhCacheManagerFactoryBean ehCacheManager() throws IOException {
		EhCacheManagerFactoryBean factory = new EhCacheManagerFactoryBean();
		factory.setCacheManagerName(CacheManager.DEFAULT_NAME);
		factory.setShared(true);
		return factory;
	}
}
