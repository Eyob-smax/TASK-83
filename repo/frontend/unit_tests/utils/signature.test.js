import { describe, it, expect } from 'vitest'
import { generateNonce, generateTimestamp, computeSignature } from '../../src/utils/signature.js'

describe('signature utilities', () => {
  it('generateNonce returns a UUID-like string', () => {
    const nonce = generateNonce()
    expect(nonce).toBeTruthy()
    expect(nonce.length).toBeGreaterThan(20)
    expect(nonce).toMatch(/^[0-9a-f-]+$/)
  })

  it('generateNonce produces unique values', () => {
    const n1 = generateNonce()
    const n2 = generateNonce()
    expect(n1).not.toBe(n2)
  })

  it('generateTimestamp returns ISO 8601 format', () => {
    const ts = generateTimestamp()
    expect(ts).toBeTruthy()
    // ISO 8601 pattern
    expect(ts).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/)
  })

  it('computeSignature returns empty string when no secret', async () => {
    const sig = await computeSignature('body', '2026-01-01T00:00:00Z', 'nonce', null)
    expect(sig).toBe('')
  })

  it('computeSignature returns empty string for empty secret', async () => {
    const sig = await computeSignature('body', '2026-01-01T00:00:00Z', 'nonce', '')
    expect(sig).toBe('')
  })
})
