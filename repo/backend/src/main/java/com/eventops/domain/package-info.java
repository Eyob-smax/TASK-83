/**
 * Core domain model: entities, enums, and value objects.
 *
 * <p>Sub-packages organize domain classes by bounded context: user, event,
 * registration, check-in, notification, finance, audit, backup, and importing.
 * Entities use JPA annotations for MySQL persistence. No business logic
 * beyond basic invariant enforcement belongs in entity classes.</p>
 */
package com.eventops.domain;
