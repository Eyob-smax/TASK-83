/**
 * Request signature computation for API calls.
 *
 * Computes HMAC-SHA256 signatures for the X-Request-Signature header
 * that the Spring Boot backend verifies.
 */

/**
 * Generate a unique nonce for request deduplication.
 * @returns {string} UUID v4 string
 */
export function generateNonce() {
  return crypto.randomUUID()
}

/**
 * Generate an ISO 8601 timestamp for the current instant.
 * @returns {string} ISO 8601 timestamp
 */
export function generateTimestamp() {
  return new Date().toISOString()
}

/**
 * Compute an HMAC-SHA256 signature over the concatenation of
 * timestamp + nonce + body using the Web Crypto API.
 *
 * @param {string} body - Serialized request body (empty string for no-body requests)
 * @param {string} timestamp - ISO 8601 timestamp
 * @param {string} nonce - UUID nonce
 * @param {string|null} secret - HMAC shared secret; if absent, returns empty string
 * @returns {Promise<string>} Base64-encoded HMAC-SHA256 signature, or empty string if no secret
 */
export async function computeSignature(body, timestamp, nonce, secret) {
  if (!secret) {
    return ''
  }

  const encoder = new TextEncoder()
  const message = encoder.encode(timestamp + nonce + body)
  const keyData = encoder.encode(secret)

  const cryptoKey = await crypto.subtle.importKey(
    'raw',
    keyData,
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign']
  )

  const signatureBuffer = await crypto.subtle.sign('HMAC', cryptoKey, message)
  const signatureBytes = new Uint8Array(signatureBuffer)

  // Convert to Base64
  let binary = ''
  for (let i = 0; i < signatureBytes.length; i++) {
    binary += String.fromCharCode(signatureBytes[i])
  }
  return btoa(binary)
}
