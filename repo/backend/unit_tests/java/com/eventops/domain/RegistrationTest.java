package com.eventops.domain;

import com.eventops.domain.registration.Registration;
import com.eventops.domain.registration.RegistrationStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RegistrationTest {

    @Test
    void defaultStatus_isConfirmed() {
        Registration reg = new Registration();
        assertEquals(RegistrationStatus.CONFIRMED, reg.getStatus());
    }

    @Test
    void waitlistPosition_nullByDefault() {
        Registration reg = new Registration();
        assertNull(reg.getWaitlistPosition());
    }
}
