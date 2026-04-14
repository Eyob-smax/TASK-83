package com.eventops.domain;

import com.eventops.domain.notification.DndRule;
import org.junit.jupiter.api.Test;
import java.time.LocalTime;
import static org.junit.jupiter.api.Assertions.*;

class DndRuleTest {

    @Test
    void defaultDndStart_is21h() {
        DndRule rule = new DndRule();
        assertEquals(LocalTime.of(21, 0), rule.getStartTime());
    }

    @Test
    void defaultDndEnd_is7h() {
        DndRule rule = new DndRule();
        assertEquals(LocalTime.of(7, 0), rule.getEndTime());
    }

    @Test
    void defaultEnabled_true() {
        DndRule rule = new DndRule();
        assertTrue(rule.isEnabled());
    }
}
