/**
 * Authentication and session management.
 *
 * <p>Handles local username/password login with BCrypt password hashing,
 * session lifecycle management for offline local-network operation, and
 * anti-bot login throttling (5 failed attempts triggers 15-minute lockout).</p>
 */
package com.eventops.security.auth;
