import { describe, it, expect } from 'vitest'
import { ROLES, CHECKIN } from '../../src/utils/constants.js'

describe('utils/constants', () => {
  it('ROLES exports exactly 4 role keys matching backend enum', () => {
    expect(Object.keys(ROLES)).toHaveLength(4)
  })

  it('CHECKIN passcode config matches backend requirements', () => {
    expect(CHECKIN.PASSCODE_DIGITS).toBe(6)
    expect(CHECKIN.PASSCODE_INTERVAL_MS).toBe(60000)
  })
})
