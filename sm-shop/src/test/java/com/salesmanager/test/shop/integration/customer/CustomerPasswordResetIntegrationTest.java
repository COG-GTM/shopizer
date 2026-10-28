package com.salesmanager.test.shop.integration.customer;

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

import com.salesmanager.core.business.repositories.customer.CustomerRepository;
import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.model.common.CredentialsReset;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class CustomerPasswordResetIntegrationTest extends ServicesTestSupport {
    private static final String OLD_PASSWORD = "OldPassword123";
    private static final String NEW_PASSWORD = "NewPassword123";
    private static final String REPLAY_PASSWORD = "ReplayPassword123";

    @Autowired
    private CustomerService service;
    @Autowired
    private CustomerRepository repository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Customer createAccount() throws Exception {
        String email = "reset-" + UUID.randomUUID() + "@test.com";
        PersistableCustomer request = new PersistableCustomer();
        request.setEmailAddress(email);
        request.setPassword(OLD_PASSWORD);
        request.setGender("M");
        request.setLanguage("en");
        Address billing = new Address();
        billing.setFirstName("Reset");
        billing.setLastName("Test");
        billing.setCountry("BE");
        request.setBilling(billing);
        request.setStoreCode("DEFAULT");
        assertEquals(200, testRestTemplate.postForEntity("/api/v1/customer/register",
                new HttpEntity<>(request), String.class).getStatusCodeValue());
        return service.getByNick(email, "DEFAULT");
    }

    private String issueToken(Customer entity, boolean expired) throws Exception {
        String token = UUID.randomUUID().toString();
        CredentialsReset credentials = new CredentialsReset();
        credentials.setCredentialsRequest(token);
        credentials.setCredentialsRequestExpiry(new Date(System.currentTimeMillis()
                + (expired ? -3L : 3L) * 86400000L));
        entity.setCredentialsResetRequest(credentials);
        service.save(entity);
        return token;
    }

    private Customer reload(Long id) {
        return repository.findById(id).get();
    }

    private int verifyToken(String token) {
        int status = testRestTemplate.getForEntity("/api/v1/customer/DEFAULT/reset/" + token,
                String.class).getStatusCodeValue();
        System.out.println("RESET_STATUS customer GET verify=" + status);
        return status;
    }

    private int resetToken(String token, String password) {
        java.util.Map<String, String> body = new java.util.HashMap<>();
        body.put("password", password);
        body.put("repeatPassword", password);
        int status = testRestTemplate.postForEntity("/api/v1/customer/DEFAULT/password/" + token,
                new HttpEntity<>(body), String.class).getStatusCodeValue();
        System.out.println("RESET_STATUS customer POST reset=" + status);
        return status;
    }

    private int login(String email, String password) {
        ResponseEntity<String> response = testRestTemplate.postForEntity(
                "/api/v1/customer/login",
                new HttpEntity<>(new AuthenticationRequest(email, password)), String.class);
        System.out.println("RESET_STATUS customer login " + password + "=" + response.getStatusCodeValue());
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
        Customer entity = createAccount();
        String email = entity.getEmailAddress();
        String token = issueToken(entity, false);
        assertEquals(200, login(email, OLD_PASSWORD));
        assertEquals(200, verifyToken(token));
        assertEquals(200, resetToken(token, NEW_PASSWORD));
        assertNull(reload(entity.getId()).getCredentialsResetRequest());
        assertEquals(200, login(email, NEW_PASSWORD));
        assertRejected(login(email, OLD_PASSWORD));
        String password = reload(entity.getId()).getPassword();
        assertRejected(resetToken(token, REPLAY_PASSWORD));
        assertEquals(password, reload(entity.getId()).getPassword());
        assertRejected(login(email, REPLAY_PASSWORD));
        assertRejected(verifyToken(token));
    }

    @Test
    public void expiredTokenCannotChangePassword() throws Exception {
        Customer entity = createAccount();
        String token = issueToken(entity, true);
        String password = reload(entity.getId()).getPassword();
        assertRejected(resetToken(token, NEW_PASSWORD));
        assertEquals(password, reload(entity.getId()).getPassword());
        assertEquals(token, reload(entity.getId()).getCredentialsResetRequest().getCredentialsRequest());
        assertEquals(200, login(entity.getEmailAddress(), OLD_PASSWORD));
        assertRejected(login(entity.getEmailAddress(), NEW_PASSWORD));
    }

    @Test
    public void wrongRepositoryTokenDoesNotClearRequest() throws Exception {
        Customer entity = createAccount();
        String token = issueToken(entity, false);
        assertEquals(0, repository.clearResetPasswordToken(entity.getId(), "wrong-token"));
        assertEquals(token, reload(entity.getId()).getCredentialsResetRequest().getCredentialsRequest());
    }

    @Test
    public void staleServiceTokenDoesNotChangePassword() throws Exception {
        Customer entity = createAccount();
        String token = issueToken(entity, false);
        String password = reload(entity.getId()).getPassword();
        assertFalse(service.resetPassword(reload(entity.getId()), "stale-token",
                passwordEncoder.encode(NEW_PASSWORD)));
        assertEquals(password, reload(entity.getId()).getPassword());
        assertEquals(token, reload(entity.getId()).getCredentialsResetRequest().getCredentialsRequest());
    }

    @Test
    public void concurrentServiceResetsHaveExactlyOneWinner() throws Exception {
        Customer entity = createAccount();
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
                    Customer fresh = reload(entity.getId());
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
            assertEquals(passwords.get(winners.get(0)), reload(entity.getId()).getPassword());
            assertNull(reload(entity.getId()).getCredentialsResetRequest());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void failedSaveRollsBackTokenConsumption() throws Exception {
        Customer entity = createAccount();
        String token = issueToken(entity, false);
        String password = reload(entity.getId()).getPassword();
        Customer invalid = reload(entity.getId());
        invalid.setEmailAddress(null);
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
        assertEquals(password, reload(entity.getId()).getPassword());
    }
}
