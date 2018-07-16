package za.org.grassroot;

import net.sf.ehcache.CacheManager;
import org.springframework.cache.ehcache.EhCacheManagerFactoryBean;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import za.org.grassroot.core.NonLocalDatabaseConfig;

import java.io.IOException;

/**
 * @author Lesetse Kimwaga
 */

@Configuration
@EnableAspectJAutoProxy
@EnableGlobalMethodSecurity(prePostEnabled = true)
@ComponentScan("za.org.grassroot")
@Import({NonLocalDatabaseConfig.class})
public class GrassrootCoreConfig {

	@Bean
	public PasswordEncoder getPasswordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean(name = "cacheManager")
	public EhCacheManagerFactoryBean ehCacheManager() throws IOException {
		EhCacheManagerFactoryBean factory = new EhCacheManagerFactoryBean();
		factory.setCacheManagerName(CacheManager.DEFAULT_NAME);
		factory.setShared(true);
		return factory;
	}

}
