package com.eventops.scheduler;

import com.eventops.service.checkin.PasscodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Rotates 6-digit check-in passcodes every 60 seconds for active sessions.
 */
@Component
public class PasscodeRotationScheduler {

    private static final Logger log = LoggerFactory.getLogger(PasscodeRotationScheduler.class);

    private final PasscodeService passcodeService;

    public PasscodeRotationScheduler(PasscodeService passcodeService) {
        this.passcodeService = passcodeService;
    }

    @Scheduled(fixedRate = 60000)
    public void rotatePasscodes() {
        try {
            passcodeService.rotateAllActiveSessionPasscodes();
            log.debug("Passcode rotation completed");
        } catch (Exception e) {
            log.error("Passcode rotation failed: {}", e.getMessage());
        }
    }
}
