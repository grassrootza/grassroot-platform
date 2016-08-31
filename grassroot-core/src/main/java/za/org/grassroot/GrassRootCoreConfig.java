package za.org.grassroot;


import net.sf.ehcache.CacheManager;
import org.springframework.cache.ehcache.EhCacheManagerFactoryBean;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import za.org.grassroot.core.ProductionDatabaseConfig;
import za.org.grassroot.core.StagingDatabaseConfig;
import za.org.grassroot.core.StandaloneDatabaseConfig;
import za.org.grassroot.core.StandaloneLocalPGConfig;
import za.org.grassroot.core.domain.BaseRoles;

import java.io.IOException;

/**
 * @author Lesetse Kimwaga
 */

@Configuration
@EnableAspectJAutoProxy
@EnableGlobalMethodSecurity(prePostEnabled = true)
@ComponentScan("za.org.grassroot")
@Import({StandaloneDatabaseConfig.class, StandaloneLocalPGConfig.class, StagingDatabaseConfig.class, ProductionDatabaseConfig.class})

public class GrassRootCoreConfig {

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

	public GrantedAuthority administratorRole() {
		return new SimpleGrantedAuthority(BaseRoles.ROLE_SYSTEM_ADMIN);
	}
}
