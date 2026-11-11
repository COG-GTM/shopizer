package com.salesmanager.test.configuration;

import com.salesmanager.core.business.configuration.DataConfiguration;
import com.salesmanager.core.business.utils.CacheUtils;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.currency.Currency;
import java.util.Collections;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.SharedCacheMode;
import org.hibernate.SessionFactory;
import org.hibernate.cache.jcache.internal.JCacheRegionFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportResource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(JCacheIntegrationTest.Config.class)
@TestPropertySource(properties = {
    "db.driverClass=org.h2.Driver", "db.jdbcUrl=jdbc:h2:mem:jcache;DB_CLOSE_DELAY=-1",
    "db.user=sa", "db.password=", "hibernate.hbm2ddl.auto=create-drop",
    "hibernate.dialect=org.hibernate.dialect.H2Dialect", "db.show.sql=false",
    "db.preferredTestQuery=SELECT 1", "db.schema=PUBLIC", "db.minPoolSize=1", "db.maxPoolSize=3"
})
class JCacheIntegrationTest {
  @Configuration
  @Import(DataConfiguration.class)
  @ImportResource("classpath:/spring/shopizer-core-ehcache.xml")
  static class Config {
    @Bean
    static BeanPostProcessor enableSelectiveCaching() {
      return new BeanPostProcessor() {
        @Override
        public Object postProcessBeforeInitialization(Object bean, String name) {
          if (bean instanceof LocalContainerEntityManagerFactoryBean) {
            ((LocalContainerEntityManagerFactoryBean) bean).setSharedCacheMode(SharedCacheMode.ENABLE_SELECTIVE);
          }
          return bean;
        }
      };
    }

    @Bean
    ReferenceLookup referenceLookup() {
      return new ReferenceLookup();
    }
  }

  static class ReferenceLookup {
    int calls;

    public int getCalls() {
      return calls;
    }

    @Cacheable("countrByCode")
    public String lookup(String code) {
      calls++;
      if (code.equals("error")) {
        throw new IllegalArgumentException(code);
      }
      return code;
    }
  }

  @Autowired private EntityManagerFactory emf;
  @Autowired private javax.cache.CacheManager springCacheManager;
  @Autowired private JCacheCacheManager serviceCacheManager;
  @Autowired private ReferenceLookup lookup;

  @Test
  void cacheUtilsIteratesAndRemovesJCacheEntries() throws Exception {
    CacheUtils utils = new CacheUtils();
    ReflectionTestUtils.setField(utils, "cache", serviceCacheManager.getCache("com.shopizer.OBJECT_CACHE"));
    javax.cache.Cache<Object, Object> nativeCache = springCacheManager.getCache("com.shopizer.OBJECT_CACHE");
    nativeCache.clear();
    MerchantStore store = new MerchantStore();
    assertTrue(utils.getCacheKeys(store).isEmpty());
    assertNull(utils.getFromCache("1_ab540"));
    utils.putInCache("value", "1_ab540");
    utils.putInCache("reference", "REFERENCE");
    nativeCache.put(42, "non-string key");
    assertEquals("value", utils.getFromCache("1_ab540"));
    assertEquals(Collections.singletonList("ab540"), utils.getCacheKeys(store));
    utils.removeAllFromCache(store);
    assertTrue(utils.getCacheKeys(store).isEmpty());
    assertNull(utils.getFromCache("1_ab540"));
    assertEquals("reference", utils.getFromCache("REFERENCE"));
    utils.removeFromCache("REFERENCE");
    assertNull(utils.getFromCache("REFERENCE"));
    nativeCache.clear();
  }

  @Test
  void hibernateAndSpringShareTheManagerAndCacheHits() {
    SessionFactoryImplementor factory = emf.unwrap(SessionFactoryImplementor.class);
    JCacheRegionFactory regions = (JCacheRegionFactory) factory.getCache().getRegionFactory();
    assertSame(springCacheManager, regions.getCacheManager());
    assertSame(springCacheManager, serviceCacheManager.getCacheManager());
    assertNull(springCacheManager.getCache("countrByCode").get("ab540-test"));
    assertEquals("ab540-test", lookup.lookup("ab540-test"));
    assertEquals("ab540-test", lookup.lookup("ab540-test"));
    assertEquals("ab540-test", springCacheManager.getCache("countrByCode").get("ab540-test"));
    assertEquals(1, lookup.getCalls());
    springCacheManager.getCache("countrByCode").remove("ab540-test");
    assertThrows(IllegalArgumentException.class, () -> lookup.lookup("error"));
    assertNull(springCacheManager.getCache("countrByCode").get("error"));

    EntityManager em = emf.createEntityManager();
    Currency currency = new Currency();
    currency.setCurrency(java.util.Currency.getInstance("CAD"));
    currency.setName("Canadian dollar");
    em.getTransaction().begin();
    em.persist(currency);
    em.getTransaction().commit();
    em.close();
    emf.getCache().evictAll();
    Statistics statistics = emf.unwrap(SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(true);
    statistics.clear();
    for (int i = 0; i < 2; i++) {
      em = emf.createEntityManager();
      em.getTransaction().begin();
      assertEquals("CAD", em.find(Currency.class, currency.getId()).getCode());
      em.getTransaction().commit();
      em.close();
    }
    assertTrue(emf.getCache().contains(Currency.class, currency.getId()));
    assertTrue(statistics.getSecondLevelCacheHitCount() > 0);
  }
}
