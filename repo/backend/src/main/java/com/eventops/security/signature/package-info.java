/**
 * Request signature verification.
 *
 * <p>Implements HMAC-SHA256 signature validation for incoming API requests.
 * Parses X-Request-Signature, X-Request-Timestamp, and X-Request-Nonce
 * headers. Enforces replay protection via timestamp age and nonce
 * deduplication.</p>
 */
package com.eventops.security.signature;
