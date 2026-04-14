package com.eventops.domain;

import com.eventops.domain.event.EventSession;
import com.eventops.domain.event.SessionStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EventSessionTest {

    @Test
    void remainingSeats_calculatesCorrectly() {
        EventSession session = new EventSession();
        session.setMaxCapacity(50);
        session.setCurrentRegistrations(30);
        assertEquals(20, session.getRemainingSeats());
    }

    @Test
    void remainingSeats_neverNegative() {
        EventSession session = new EventSession();
        session.setMaxCapacity(10);
        session.setCurrentRegistrations(15);
        assertEquals(0, session.getRemainingSeats());
    }

    @Test
    void isFull_trueWhenAtCapacity() {
        EventSession session = new EventSession();
        session.setMaxCapacity(10);
        session.setCurrentRegistrations(10);
        assertTrue(session.isFull());
    }

    @Test
    void isFull_trueWhenOverCapacity() {
        EventSession session = new EventSession();
        session.setMaxCapacity(10);
        session.setCurrentRegistrations(12);
        assertTrue(session.isFull());
    }

    @Test
    void isFull_falseWhenBelowCapacity() {
        EventSession session = new EventSession();
        session.setMaxCapacity(10);
        session.setCurrentRegistrations(9);
        assertFalse(session.isFull());
    }

    @Test
    void defaultCheckinWindow_30before15after() {
        EventSession session = new EventSession();
        assertEquals(30, session.getCheckinWindowBeforeMinutes());
        assertEquals(15, session.getCheckinWindowAfterMinutes());
    }

    @Test
    void defaultDeviceBinding_false() {
        EventSession session = new EventSession();
        assertFalse(session.isDeviceBindingRequired());
    }

    @Test
    void defaultStatus_isDraft() {
        EventSession session = new EventSession();
        assertEquals(SessionStatus.DRAFT, session.getStatus());
    }
}
