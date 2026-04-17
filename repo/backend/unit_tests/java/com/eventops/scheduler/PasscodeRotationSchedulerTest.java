package com.eventops.scheduler;

import com.eventops.service.checkin.PasscodeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasscodeRotationSchedulerTest {

    @Mock PasscodeService passcodeService;
    @InjectMocks PasscodeRotationScheduler scheduler;

    @Test
    void rotatePasscodes_callsService() {
        scheduler.rotatePasscodes();
        verify(passcodeService).rotateAllActiveSessionPasscodes();
    }

    @Test
    void rotatePasscodes_swallowsExceptions() {
        doThrow(new RuntimeException("boom")).when(passcodeService).rotateAllActiveSessionPasscodes();
        assertDoesNotThrow(scheduler::rotatePasscodes);
    }
}
