import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as financeApi from '@/api/finance'

export const useFinanceStore = defineStore('finance', () => {
  const periods = ref([])
  const accounts = ref([])
  const costCenters = ref([])
  const rules = ref([])
  const postings = ref([])
  const currentLineItems = ref([])
  const loading = ref(false)
  const error = ref(null)
  const successMessage = ref(null)

  async function fetchPeriods() {
    loading.value = true; error.value = null
    try { const res = await financeApi.listPeriods(); periods.value = res.data.data }
    catch (e) { error.value = e.response?.data?.message || 'Failed to load periods' }
    finally { loading.value = false }
  }

  async function createPeriod(data) {
    loading.value = true; error.value = null
    try {
      const res = await financeApi.createPeriod(data)
      periods.value.push(res.data.data)
      successMessage.value = 'Period created'
    } catch (e) { error.value = e.response?.data?.message || 'Failed to create period' }
    finally { loading.value = false }
  }

  async function fetchAccounts() {
    loading.value = true; error.value = null
    try { const res = await financeApi.listAccounts(); accounts.value = res.data.data }
    catch (e) { error.value = e.response?.data?.message || 'Failed to load accounts' }
    finally { loading.value = false }
  }

  async function createAccount(data) {
    loading.value = true; error.value = null
    try {
      const res = await financeApi.createAccount(data)
      accounts.value.push(res.data.data)
      successMessage.value = 'Account created'
    } catch (e) { error.value = e.response?.data?.message || 'Failed to create account' }
    finally { loading.value = false }
  }

  async function fetchCostCenters() {
    loading.value = true; error.value = null
    try { const res = await financeApi.listCostCenters(); costCenters.value = res.data.data }
    catch (e) { error.value = e.response?.data?.message || 'Failed to load cost centers' }
    finally { loading.value = false }
  }

  async function createCostCenter(data) {
    loading.value = true; error.value = null
    try {
      const res = await financeApi.createCostCenter(data)
      costCenters.value.push(res.data.data)
      successMessage.value = 'Cost center created'
    } catch (e) { error.value = e.response?.data?.message || 'Failed to create cost center' }
    finally { loading.value = false }
  }

  async function fetchRules() {
    loading.value = true; error.value = null
    try { const res = await financeApi.listRules(); rules.value = res.data.data }
    catch (e) { error.value = e.response?.data?.message || 'Failed to load rules' }
    finally { loading.value = false }
  }

  async function createRule(data) {
    loading.value = true; error.value = null
    try {
      const res = await financeApi.createRule(data)
      rules.value.push(res.data.data)
      successMessage.value = 'Rule created (version 1)'
    } catch (e) { error.value = e.response?.data?.message || 'Failed to create rule' }
    finally { loading.value = false }
  }

  async function updateRule(ruleId, data) {
    loading.value = true; error.value = null
    try {
      const res = await financeApi.createRule({ ...data, id: ruleId })
      const idx = rules.value.findIndex(r => r.id === ruleId)
      if (idx >= 0) rules.value[idx] = { ...rules.value[idx], active: false }
      rules.value.push(res.data.data)
      successMessage.value = 'Rule updated (new version created)'
    } catch (e) { error.value = e.response?.data?.message || 'Failed to update rule' }
    finally { loading.value = false }
  }

  async function fetchPostings(periodId) {
    loading.value = true; error.value = null
    try { const res = await financeApi.listPostings({ periodId }); postings.value = res.data.data }
    catch (e) { error.value = e.response?.data?.message || 'Failed to load postings' }
    finally { loading.value = false }
  }

  async function executePosting(data) {
    loading.value = true; error.value = null
    try {
      const res = await financeApi.executePosting(data)
      postings.value.push(res.data.data)
      successMessage.value = 'Posting executed successfully'
      return res.data.data
    } catch (e) { error.value = e.response?.data?.message || 'Posting failed'; throw e }
    finally { loading.value = false }
  }

  async function fetchLineItems(postingId) {
    loading.value = true; error.value = null
    try { const res = await financeApi.getPostingLineItems(postingId); currentLineItems.value = res.data.data }
    catch (e) { error.value = e.response?.data?.message || 'Failed to load line items' }
    finally { loading.value = false }
  }

  function clearMessages() { error.value = null; successMessage.value = null }

  return { periods, accounts, costCenters, rules, postings, currentLineItems, loading, error, successMessage, fetchPeriods, createPeriod, fetchAccounts, createAccount, fetchCostCenters, createCostCenter, fetchRules, createRule, updateRule, fetchPostings, executePosting, fetchLineItems, clearMessages }
})
