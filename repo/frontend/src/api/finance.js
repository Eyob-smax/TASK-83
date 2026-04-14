import apiClient from './client'

export function listPeriods(params) {
  return apiClient.get('/finance/periods', { params })
}

export function createPeriod(data) {
  return apiClient.post('/finance/periods', data)
}

export function listAccounts(params) {
  return apiClient.get('/finance/accounts', { params })
}

export function createAccount(data) {
  return apiClient.post('/finance/accounts', data)
}

export function listCostCenters(params) {
  return apiClient.get('/finance/cost-centers', { params })
}

export function createCostCenter(data) {
  return apiClient.post('/finance/cost-centers', data)
}

export function listRules(params) {
  return apiClient.get('/finance/rules', { params })
}

export function createRule(data) {
  return apiClient.post('/finance/rules', data)
}

export function getRuleVersions(ruleId) {
  return apiClient.get(`/finance/rules/${ruleId}/versions`)
}

export function executePosting(data) {
  return apiClient.post('/finance/postings', data)
}

export function listPostings(params) {
  return apiClient.get('/finance/postings', { params })
}

export function getPostingLineItems(postingId) {
  return apiClient.get(`/finance/postings/${postingId}/line-items`)
}
