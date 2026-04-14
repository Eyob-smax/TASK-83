package com.eventops.repository.admin;

import com.eventops.domain.admin.SecuritySettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecuritySettingsRepository extends JpaRepository<SecuritySettings, String> {
}
