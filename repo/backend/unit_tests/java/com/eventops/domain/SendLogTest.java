package com.eventops.domain;

import com.eventops.domain.notification.SendLog;
import com.eventops.domain.notification.SendStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SendLogTest {

    @Test
    void defaultMaxAttempts_is5() {
        SendLog log = new SendLog();
        assertEquals(5, log.getMaxAttempts());
    }

    @Test
    void defaultAttemptCount_is0() {
        SendLog log = new SendLog();
        assertEquals(0, log.getAttemptCount());
    }

    @Test
    void defaultStatus_isPending() {
        SendLog log = new SendLog();
        assertEquals(SendStatus.PENDING, log.getStatus());
    }
}
