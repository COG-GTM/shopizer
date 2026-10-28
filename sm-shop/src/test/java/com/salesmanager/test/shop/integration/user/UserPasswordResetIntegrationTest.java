package com.salesmanager.test.shop.integration.user;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.repositories.user.UserRepository;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.common.CredentialsReset;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class UserPasswordResetIntegrationTest extends ServicesTestSupport {
    private static final String OLD_PASSWORD = "OldPass123";
    private static final String NEW_PASSWORD = "NewPass123";
    private static final String REPLAY_PASSWORD = "Replay123";

    @Autowired
    private UserService service;
    @Autowired
    private UserRepository repository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User createAccount() throws Exception {
        String email = "reset-" + UUID.randomUUID() + "@test.com";
        PersistableUser request = new PersistableUser();
        request.setDefaultLanguage("en");
        request.setEmailAddress(email);
        request.setFirstName("Reset");
        request.setLastName("Test");
        request.setUserName(email);
        request.setPassword(OLD_PASSWORD);
        request.setRepeatPassword(OLD_PASSWORD);
        PersistableGroup group = new PersistableGroup();
        group.setName("ADMIN");
        request.getGroups().add(group);
        assertEquals(200, testRestTemplate.postForEntity("/api/v1/private/user/",
                new HttpEntity<>(request, getHeader()), String.class).getStatusCodeValue());
        return service.getByUserName(email);
    }

    private String issueToken(User entity, boolean expired) throws Exception {
        String token = UUID.randomUUID().toString();
        CredentialsReset credentials = new CredentialsReset();
        credentials.setCredentialsRequest(token);
        credentials.setCredentialsRequestExpiry(new Date(System.currentTimeMillis()
                + (expired ? -3L : 3L) * 86400000L));
        entity.setCredentialsResetRequest(credentials);
        service.save(entity);
        return token;
    }

    private User reload(Long id) {
        return repository.findById(id).get();
    }

    private int verifyToken(String token) {
        int status = testRestTemplate.getForEntity("/api/v1/user/DEFAULT/reset/" + token,
                String.class).getStatusCodeValue();
        System.out.println("RESET_STATUS user GET verify=" + status);
        return status;
    }

    private int resetToken(String token, String password) {
        java.util.Map<String, String> body = new java.util.HashMap<>();
        body.put("password", password);
        body.put("repeatPassword", password);
        int status = testRestTemplate.postForEntity("/api/v1/user/DEFAULT/password/" + token,
                new HttpEntity<>(body), String.class).getStatusCodeValue();
        System.out.println("RESET_STATUS user POST reset=" + status);
        return status;
    }

    private int login(String email, String password) {
        ResponseEntity<String> response = testRestTemplate.postForEntity(
                "/api/v1/private/login",
                new HttpEntity<>(new AuthenticationRequest(email, password)), String.class);
        System.out.println("RESET_STATUS user login " + password + "=" + response.getStatusCodeValue());
        if (response.getStatusCode().is2xxSuccessful()) {
            assertNotNull(response.getBody());
            assertTrue(response.getBody().contains("token"));
        }
        return response.getStatusCodeValue();
    }

    private void assertRejected(int status) {
        assertFalse("Expected non-2xx, got " + status, status >= 200 && status < 300);
    }

    @Test
    public void restResetIsSingleUseAndChangesLoginPassword() throws Exception {
        User entity = createAccount();
        String email = entity.getAdminName();
        String token = issueToken(entity, false);
        assertEquals(200, login(email, OLD_PASSWORD));
        assertEquals(200, verifyToken(token));
        assertEquals(200, resetToken(token, NEW_PASSWORD));
        assertNull(reload(entity.getId()).getCredentialsResetRequest());
        assertEquals(200, login(email, NEW_PASSWORD));
        assertRejected(login(email, OLD_PASSWORD));
        String password = reload(entity.getId()).getAdminPassword();
        assertRejected(resetToken(token, REPLAY_PASSWORD));
        assertEquals(password, reload(entity.getId()).getAdminPassword());
        assertRejected(login(email, REPLAY_PASSWORD));
        assertRejected(verifyToken(token));
    }

    @Test
    public void expiredTokenCannotChangePassword() throws Exception {
        User entity = createAccount();
        String token = issueToken(entity, true);
        String password = reload(entity.getId()).getAdminPassword();
        assertRejected(resetToken(token, NEW_PASSWORD));
        assertEquals(password, reload(entity.getId()).getAdminPassword());
        assertEquals(token, reload(entity.getId()).getCredentialsResetRequest().getCredentialsRequest());
        assertEquals(200, login(entity.getAdminName(), OLD_PASSWORD));
        assertRejected(login(entity.getAdminName(), NEW_PASSWORD));
    }

    @Test
    public void wrongRepositoryTokenDoesNotClearRequest() throws Exception {
        User entity = createAccount();
        String token = issueToken(entity, false);
        assertEquals(0, repository.clearResetPasswordToken(entity.getId(), "wrong-token"));
        assertEquals(token, reload(entity.getId()).getCredentialsResetRequest().getCredentialsRequest());
    }

    @Test
    public void staleServiceTokenDoesNotChangePassword() throws Exception {
        User entity = createAccount();
        String token = issueToken(entity, false);
        String password = reload(entity.getId()).getAdminPassword();
        assertFalse(service.resetPassword(reload(entity.getId()), "stale-token",
                passwordEncoder.encode(NEW_PASSWORD)));
        assertEquals(password, reload(entity.getId()).getAdminPassword());
        assertEquals(token, reload(entity.getId()).getCredentialsResetRequest().getCredentialsRequest());
    }

    @Test
    public void concurrentServiceResetsHaveExactlyOneWinner() throws Exception {
        User entity = createAccount();
        String token = issueToken(entity, false);
        int count = 8;
        List<String> passwords = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            passwords.add(passwordEncoder.encode("WinnerPassword" + i));
        }
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch loaded = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < count; i++) {
                final int index = i;
                futures.add(executor.submit(() -> {
                    User fresh = reload(entity.getId());
                    loaded.countDown();
                    assertTrue(start.await(30, TimeUnit.SECONDS));
                    return service.resetPassword(fresh, token, passwords.get(index)) ? index : -1;
                }));
            }
            assertTrue("All workers loaded their own entity", loaded.await(30, TimeUnit.SECONDS));
            start.countDown();
            List<Integer> winners = new ArrayList<>();
            for (Future<Integer> future : futures) {
                int winner = future.get(60, TimeUnit.SECONDS);
                if (winner >= 0) {
                    winners.add(winner);
                }
            }
            assertEquals(1, winners.size());
            assertEquals(passwords.get(winners.get(0)), reload(entity.getId()).getAdminPassword());
            assertNull(reload(entity.getId()).getCredentialsResetRequest());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void failedSaveRollsBackTokenConsumption() throws Exception {
        User entity = createAccount();
        String token = issueToken(entity, false);
        String password = reload(entity.getId()).getAdminPassword();
        User invalid = reload(entity.getId());
        invalid.setMerchantStore(null);
        try {
            service.resetPassword(invalid, token, passwordEncoder.encode(NEW_PASSWORD));
            fail("Save should fail");
        } catch (RuntimeException expected) {
            Throwable cause = expected;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            assertTrue(cause.toString(), cause instanceof javax.validation.ConstraintViolationException
                    || cause instanceof org.hibernate.PropertyValueException);
        }
        assertEquals(token, reload(entity.getId()).getCredentialsResetRequest().getCredentialsRequest());
        assertEquals(password, reload(entity.getId()).getAdminPassword());
    }
}
