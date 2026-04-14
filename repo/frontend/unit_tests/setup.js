/**
 * Test environment setup for EventOps frontend unit tests.
 *
 * Mocks jsdom globals that are not available by default,
 * such as navigator.onLine for offline-detection tests.
 */

// Ensure navigator.onLine defaults to true in the test environment
Object.defineProperty(globalThis.navigator, 'onLine', {
  value: true,
  writable: true,
  configurable: true
})
