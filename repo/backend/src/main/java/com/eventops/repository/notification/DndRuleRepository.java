package com.eventops.repository.notification;

import com.eventops.domain.notification.DndRule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DndRuleRepository extends JpaRepository<DndRule, String> {
}
