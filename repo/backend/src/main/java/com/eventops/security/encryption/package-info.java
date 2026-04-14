/**
 * AES-256 field-level encryption and decryption.
 *
 * <p>Provides JPA attribute converters for transparent encryption of
 * sensitive fields (attendee contacts, device identifiers) at rest.
 * Also provides masked-by-default display helpers that return masked
 * values unless explicit decryption is authorized.</p>
 */
package com.eventops.security.encryption;
