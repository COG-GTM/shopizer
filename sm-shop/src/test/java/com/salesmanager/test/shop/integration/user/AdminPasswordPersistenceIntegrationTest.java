package com.salesmanager.test.shop.integration.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Date;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.store.security.JWTTokenUtil;
import com.salesmanager.shop.store.security.user.JWTUser;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@Transactional
public class AdminPasswordPersistenceIntegrationTest extends ServicesTestSupport {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    @Qualifier("jwtAdminDetailsService")
    private UserDetailsService adminDetails;

    @Autowired
    private JWTTokenUtil tokens;

    private User admin() {
        return entityManager.find(User.class, 1L);
    }

    private User reload(User user) {
        Long id = user.getId();
        entityManager.flush();
        entityManager.clear();
        return entityManager.find(User.class, id);
    }

    @Test
    public void nullLegacyMarkerSurvivesRoundTripAndStillAllowsTokens() {
        User user = admin();
        user.setPasswordChangedDate(null);
        user.setActive(true);
        User reloaded = reload(user);
        assertNull(reloaded.getPasswordChangedDate());
        JWTUser details = (JWTUser) adminDetails.loadUserByUsername(reloaded.getAdminName());
        assertNull(details.getLastPasswordResetDate());
        assertTrue(tokens.validateToken(tokens.generateToken(details), details));
    }

    @Test
    public void numericMarkerRetainsMillisecondsAndDrivesRevocationAfterReload() {
        User user = admin();
        user.setActive(true);
        user.setPasswordChangedDate(null);
        entityManager.flush();
        JWTUser before = (JWTUser) adminDetails.loadUserByUsername(user.getAdminName());
        String token = tokens.generateToken(before);
        Date issued = tokens.getPreciseIssuedAtDateFromToken(token);
        Date changed = new Date(issued.getTime() + 123L);
        user.setPasswordChangedDate(changed);
        User reloaded = reload(user);
        assertEquals(changed, reloaded.getPasswordChangedDate());
        JWTUser after = (JWTUser) adminDetails.loadUserByUsername(reloaded.getAdminName());
        assertEquals(changed, after.getLastPasswordResetDate());
        assertFalse(tokens.validateToken(token, after));
    }

    @Test
    public void persistedDeactivationIsReflectedByRealAdminDetailsService() {
        User user = admin();
        user.setPasswordChangedDate(null);
        user.setActive(true);
        entityManager.flush();
        JWTUser enabled = (JWTUser) adminDetails.loadUserByUsername(user.getAdminName());
        String token = tokens.generateToken(enabled);
        assertTrue(tokens.validateToken(token, enabled));
        user.setActive(false);
        User reloaded = reload(user);
        JWTUser disabled = (JWTUser) adminDetails.loadUserByUsername(reloaded.getAdminName());
        assertFalse(disabled.isEnabled());
        assertFalse(tokens.validateToken(token, disabled));
    }
}
