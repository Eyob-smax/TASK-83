import { describe, it, expect } from 'vitest'
import { formatDateTime, formatDate, formatTime, formatCurrency, maskValue, formatRelativeTime } from '../../src/utils/formatters.js'

describe('formatters', () => {
  it('formatDateTime returns dash for null', () => {
    expect(formatDateTime(null)).toBe('\u2014')
  })

  it('formatDateTime formats ISO string', () => {
    const result = formatDateTime('2026-04-13T10:30:00Z')
    expect(result).toBeTruthy()
    expect(result).not.toBe('\u2014')
  })

  it('formatDate returns date only', () => {
    const result = formatDate('2026-04-13T10:30:00Z')
    expect(result).toContain('2026')
  })

  it('formatCurrency formats USD', () => {
    expect(formatCurrency(1234.56)).toContain('1,234.56')
  })

  it('formatCurrency returns dash for null', () => {
    expect(formatCurrency(null)).toBe('\u2014')
  })

  it('maskValue hides most characters', () => {
    const masked = maskValue('sensitive@email.com')
    expect(masked).toContain('****')
    expect(masked).toContain('.com')
    expect(masked).not.toContain('sensitive')
  })

  it('maskValue returns dash for empty', () => {
    expect(maskValue(null)).toBe('\u2014')
    expect(maskValue('')).toBe('\u2014')
  })

  it('formatRelativeTime shows just now for recent', () => {
    const recent = new Date().toISOString()
    expect(formatRelativeTime(recent)).toBe('just now')
  })

  it('formatRelativeTime shows minutes ago within an hour', () => {
    const mins = new Date(Date.now() - 5 * 60 * 1000).toISOString()
    expect(formatRelativeTime(mins)).toContain('min ago')
  })

  it('formatRelativeTime shows hours ago within a day', () => {
    const hrs = new Date(Date.now() - 3 * 60 * 60 * 1000).toISOString()
    expect(formatRelativeTime(hrs)).toContain('h ago')
  })

  it('formatRelativeTime falls back to formatDate for older than 1 day', () => {
    const old = new Date(Date.now() - 3 * 24 * 60 * 60 * 1000).toISOString()
    const out = formatRelativeTime(old)
    expect(out).not.toContain('ago')
    expect(out).toMatch(/\d{4}/)
  })

  it('formatRelativeTime returns dash for null', () => {
    expect(formatRelativeTime(null)).toBe('\u2014')
  })

  it('formatTime returns dash for null', () => {
    expect(formatTime(null)).toBe('\u2014')
  })

  it('formatTime formats ISO string to clock time', () => {
    const out = formatTime('2026-04-13T10:30:00Z')
    expect(out).toBeTruthy()
    expect(out).not.toBe('\u2014')
  })

  it('formatDate returns dash for null', () => {
    expect(formatDate(null)).toBe('\u2014')
  })

  it('maskValue with short value masks as ****', () => {
    expect(maskValue('ab')).toBe('****')
  })
})
