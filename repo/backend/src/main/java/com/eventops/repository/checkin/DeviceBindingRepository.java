package com.eventops.repository.checkin;

import com.eventops.domain.checkin.DeviceBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;

public interface DeviceBindingRepository extends JpaRepository<DeviceBinding, String> {
    Optional<DeviceBinding> findByUserIdAndBindingDate(String userId, LocalDate bindingDate);
    boolean existsByUserIdAndBindingDate(String userId, LocalDate bindingDate);
}
