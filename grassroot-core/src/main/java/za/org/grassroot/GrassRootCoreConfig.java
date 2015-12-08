package za.org.grassroot;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.ehcache.EhCacheFactoryBean;
import org.springframework.cache.ehcache.EhCacheManagerFactoryBean;

import net.sf.ehcache.CacheException;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;

import org.springframework.context.annotation.*;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.acls.AclPermissionEvaluator;
import org.springframework.security.acls.domain.*;
import org.springframework.security.acls.jdbc.BasicLookupStrategy;
import org.springframework.security.acls.jdbc.JdbcMutableAclService;
import org.springframework.security.acls.jdbc.LookupStrategy;
import org.springframework.security.acls.model.AclCache;
import org.springframework.security.acls.model.MutableAclService;
import org.springframework.security.acls.model.PermissionGrantingStrategy;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import za.org.grassroot.core.*;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.repository.PermissionRepository;
import za.org.grassroot.core.security.CustomPermissionFactory;

import javax.sql.DataSource;
import java.io.IOException;

/**
 * @author Lesetse Kimwaga
 */

@Configuration
@EnableAspectJAutoProxy
@EnableGlobalMethodSecurity(prePostEnabled=true)
@ComponentScan("za.org.grassroot")
@Import({HerokuDatabaseConfig.class,StandaloneDatabaseConfig.class, StandaloneLocalPGConfig.class})

public class GrassRootCoreConfig {

    private static final String ACL_CACHE_NAME = "GRASS_ROOT_CACHE";

    @Autowired
    DataSource dataSource;

    @Autowired
    PermissionRepository permissionRepository;

    @Autowired
    ConfigurableEnvironment environment;

    @Bean
    public PasswordEncoder getPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }


    @Bean(name = "cacheManager")
    public CacheManager ehCacheManager() throws IOException {
        EhCacheManagerFactoryBean factory = new EhCacheManagerFactoryBean();
        factory.setCacheManagerName(CacheManager.DEFAULT_NAME);
        factory.setShared(true);
        factory.afterPropertiesSet();
        return factory.getObject();
    }

    @Bean(name = "ehcache")
    public Ehcache ehCacheFactory() throws CacheException, IOException {
        EhCacheFactoryBean factory = new EhCacheFactoryBean();
        factory.setCacheManager(ehCacheManager());
        factory.setCacheName(ACL_CACHE_NAME);
        factory.afterPropertiesSet();
        return factory.getObject();
    }


    @Bean
    public AclAuthorizationStrategy aclAuthorizationStrategy() {
        return new AclAuthorizationStrategyImpl(administratorRole(),
                administratorRole(),
                administratorRole());
    }

    public GrantedAuthority administratorRole() {
        return new SimpleGrantedAuthority(BaseRoles.ROLE_SYSTEM_ADMIN);
    }

    @Bean
    public PermissionFactory aclPermissionFactory() {
        return new CustomPermissionFactory(permissionRepository);
    }

    @Bean
    public AuditLogger aclAuditLogger() {
        return new ConsoleAuditLogger();
    }

    @Bean
    public PermissionGrantingStrategy aclPermissionGrantingStrategy() {
        return new DefaultPermissionGrantingStrategy(aclAuditLogger());
    }

    @Bean
    public AclCache aclCache() throws CacheException, IOException {
        return new EhCacheBasedAclCache(ehCacheFactory(), aclPermissionGrantingStrategy(), aclAuthorizationStrategy());
    }

    @Bean
    public LookupStrategy aclLookupStrategy() throws IOException {
        BasicLookupStrategy lookupStrategy = new BasicLookupStrategy(dataSource,
                aclCache(), aclAuthorizationStrategy(), aclPermissionGrantingStrategy());
        lookupStrategy.setPermissionFactory(aclPermissionFactory());
        return lookupStrategy;
    }

    @Bean
    public MutableAclService aclService() throws CacheException, IOException {
        JdbcMutableAclService aclService = new JdbcMutableAclService(dataSource, aclLookupStrategy(), aclCache());

        if(!environment.acceptsProfiles(GrassRootApplicationProfiles.INMEMORY))
        {
            aclService.setClassIdentityQuery("select currval(pg_get_serial_sequence('acl_class', 'id'))");
            aclService.setSidIdentityQuery("select currval(pg_get_serial_sequence('acl_sid', 'id'))");
        }

        return aclService;
    }

    @Bean
    public PermissionEvaluator permissionEvaluator() throws IOException {
        return new AclPermissionEvaluator(aclService());
    }

    @Bean
    public  MethodSecurityExpressionHandler expressionHandler() throws IOException {

        DefaultMethodSecurityExpressionHandler methodSecurityExpressionHandler =  new  DefaultMethodSecurityExpressionHandler();
        methodSecurityExpressionHandler.setPermissionEvaluator(permissionEvaluator());
        return  methodSecurityExpressionHandler;
    }


}
