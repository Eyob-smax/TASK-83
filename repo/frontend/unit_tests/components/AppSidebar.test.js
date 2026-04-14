import { describe, it, expect } from 'vitest'

describe('AppSidebar role filtering', () => {
  const allNavItems = [
    { label: 'Sessions', roles: null },
    { label: 'Waitlist', roles: null },
    { label: 'Notifications', roles: null },
    { label: 'Check-In', roles: ['EVENT_STAFF', 'SYSTEM_ADMIN'] },
    { label: 'Imports', roles: ['EVENT_STAFF', 'SYSTEM_ADMIN'] },
    { label: 'Finance', roles: ['FINANCE_MANAGER', 'SYSTEM_ADMIN'] },
    { label: 'Exports', roles: ['EVENT_STAFF', 'FINANCE_MANAGER', 'SYSTEM_ADMIN'] },
    { label: 'Audit Log', roles: ['SYSTEM_ADMIN'] },
    { label: 'Users', roles: ['SYSTEM_ADMIN'] },
    { label: 'Backups', roles: ['SYSTEM_ADMIN'] },
    { label: 'Security', roles: ['SYSTEM_ADMIN'] }
  ]

  function visibleItems(userRoles) {
    return allNavItems.filter(item =>
      !item.roles || item.roles.some(r => userRoles.includes(r))
    )
  }

  it('attendee sees 3 items', () => {
    expect(visibleItems(['ATTENDEE'])).toHaveLength(3)
  })

  it('staff sees 6 items', () => {
    expect(visibleItems(['EVENT_STAFF'])).toHaveLength(6)
  })

  it('finance manager sees 5 items', () => {
    expect(visibleItems(['FINANCE_MANAGER'])).toHaveLength(5)
  })

  it('admin sees all 11 items', () => {
    expect(visibleItems(['SYSTEM_ADMIN'])).toHaveLength(11)
  })

  it('unknown role sees only public items', () => {
    expect(visibleItems(['UNKNOWN'])).toHaveLength(3)
  })
})
