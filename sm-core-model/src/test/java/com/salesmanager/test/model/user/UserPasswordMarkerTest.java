package com.salesmanager.test.model.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Date;

import org.junit.Test;

import com.salesmanager.core.model.user.User;

public class UserPasswordMarkerTest {

    @Test
    public void legacyUserAndNullMarkerRemainSupported() {
        User user = new User("legacy", "hash", "legacy@example.com");
        assertNull(user.getPasswordChangedDate());
        user.setPasswordChangedDate(new Date(1700000000123L));
        user.setPasswordChangedDate(null);
        assertNull(user.getPasswordChangedDate());
    }

    @Test
    public void markerDefensivelyCopiesDatesWithMillisecondPrecision() {
        User user = new User();
        Date marker = new Date(1700000000123L);
        user.setPasswordChangedDate(marker);
        marker.setTime(0L);
        assertEquals(1700000000123L, user.getPasswordChangedDate().getTime());
        user.getPasswordChangedDate().setTime(0L);
        assertEquals(1700000000123L, user.getPasswordChangedDate().getTime());
    }

    @Test
    public void unchangedHashDoesNotRevokeTokens() {
        User user = new User("existing", "hash", "existing@example.com");
        Date marker = new Date(1700000000123L);
        user.setPasswordChangedDate(marker);
        user.setAdminPassword(new String("hash"));
        assertEquals(marker, user.getPasswordChangedDate());
    }

    @Test
    public void changedHashRecordsCurrentTimeWithoutSecondTruncation() {
        User user = new User("existing", "hash", "existing@example.com");
        user.setPasswordChangedDate(new Date(1L));
        long before = System.currentTimeMillis();
        user.setAdminPassword("replacement");
        long after = System.currentTimeMillis();
        assertNotNull(user.getPasswordChangedDate());
        assertTrue(user.getPasswordChangedDate().getTime() >= before);
        assertTrue(user.getPasswordChangedDate().getTime() <= after);
        assertEquals("replacement", user.getAdminPassword());
    }
}
