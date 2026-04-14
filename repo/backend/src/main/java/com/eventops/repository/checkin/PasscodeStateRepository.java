package com.eventops.repository.checkin;

import com.eventops.domain.checkin.PasscodeState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasscodeStateRepository extends JpaRepository<PasscodeState, String> {
}
