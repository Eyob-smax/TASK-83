import { describe, it, expect } from 'vitest'
import { formatDateTime, formatDate, formatCurrency, maskValue, formatRelativeTime } from '../../src/utils/formatters.js'

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
})
