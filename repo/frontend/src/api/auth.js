import apiClient from './client'

export function login(credentials) {
  return apiClient.post('/auth/login', credentials)
}

export function logout() {
  return apiClient.post('/auth/logout')
}

export function createAccount(userData) {
  return apiClient.post('/auth/register', userData)
}

export function refreshSession() {
  return apiClient.post('/auth/refresh')
}

export function getCurrentUser() {
  return apiClient.get('/auth/me')
}
