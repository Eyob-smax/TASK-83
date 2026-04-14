# Database Migrations

## Overview

This directory contains Flyway-managed database migration scripts for the EventOps platform MySQL 8.x database.

## Conventions

- **Versioned migrations**: `V{NNN}__{description}.sql` - applied once in order
- **Repeatable migrations**: `R{NNN}__{description}.sql` - re-applied when checksum changes
- All migrations run against the `eventops` database
- Flyway is configured in `src/main/resources/application.yml`

## Location

Migration scripts are placed in `migrations/` and are picked up by Flyway via the `filesystem:database/migrations` location configured in the Maven plugin and Spring Boot config.

## Current State

Current versioned migrations:

- `V001__initial_schema.sql` - initial schema for users, events, registrations, check-in, notifications, imports, finance, audit, and backups
- `V002__add_read_at_to_send_logs.sql` - adds `read_at` support for notification send logs
- `V003__add_security_settings.sql` - adds persisted admin-managed security settings
