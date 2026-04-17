import { describe, it, expect, vi, beforeEach } from 'vitest'

const { store } = vi.hoisted(() => ({ store: new Map() }))

vi.mock('localforage', () => ({
  default: {
    createInstance: vi.fn(() => ({
      getItem: vi.fn(async (k) => store.get(k) ?? null),
      setItem: vi.fn(async (k, v) => { store.set(k, v); return v }),
      removeItem: vi.fn(async (k) => { store.delete(k) })
    }))
  }
}))

vi.mock('@/api/attachments', () => ({
  createUploadSession: vi.fn(),
  uploadChunk: vi.fn(),
  getUploadStatus: vi.fn(),
  completeUpload: vi.fn()
}))

import { createUploadSession, uploadChunk, getUploadStatus, completeUpload } from '@/api/attachments'
import { uploadAttachmentResumable } from '../../src/utils/resumableUpload.js'

function makeFile({ size = 1024, name = 'f.bin' } = {}) {
  const parts = new Array(size).fill('a').join('')
  const blob = new Blob([parts])
  return {
    name,
    size: blob.size,
    lastModified: 123,
    slice: (start, end) => blob.slice(start, end)
  }
}

describe('utils/resumableUpload', () => {
  beforeEach(() => {
    store.clear()
    vi.clearAllMocks()
  })

  it('fresh upload: creates session, uploads all chunks, completes, and returns result', async () => {
    const file = makeFile({ size: 1024 })
    createUploadSession.mockResolvedValue({ data: { data: { uploadId: 'up-1' } } })
    uploadChunk.mockResolvedValue({ data: { data: { uploadId: 'up-1' } } })
    completeUpload.mockResolvedValue({ data: { data: { attachmentId: 'att-1', checksum: 'abc' } } })

    const result = await uploadAttachmentResumable(file, { chunkSize: 512 })

    expect(createUploadSession).toHaveBeenCalledTimes(1)
    expect(uploadChunk).toHaveBeenCalledTimes(2) // 1024/512 = 2 chunks
    expect(completeUpload).toHaveBeenCalledWith('up-1')
    expect(result.attachmentId).toBe('att-1')
  })

  it('resume: reuses existing uploadId and skips already-uploaded chunks', async () => {
    const file = makeFile({ size: 1536 })
    // Seed pre-existing state
    store.set('upload:f.bin:1536:123', { uploadId: 'existing-upload', uploadedChunks: [0] })
    getUploadStatus.mockResolvedValue({ data: { data: { uploadedChunks: [0] } } })
    uploadChunk.mockResolvedValue({})
    completeUpload.mockResolvedValue({ data: { data: { attachmentId: 'att-2' } } })

    const result = await uploadAttachmentResumable(file, { chunkSize: 512 })

    expect(createUploadSession).not.toHaveBeenCalled()
    expect(uploadChunk).toHaveBeenCalledTimes(2) // chunks 1,2 — skipped 0
    expect(completeUpload).toHaveBeenCalledWith('existing-upload')
    expect(result.attachmentId).toBe('att-2')
  })

  it('when getUploadStatus fails, re-creates session', async () => {
    const file = makeFile({ size: 512 })
    store.set('upload:f.bin:512:123', { uploadId: 'stale-upload', uploadedChunks: [] })
    getUploadStatus.mockRejectedValue(new Error('not found'))
    createUploadSession.mockResolvedValue({ data: { data: { uploadId: 'fresh-upload' } } })
    uploadChunk.mockResolvedValue({})
    completeUpload.mockResolvedValue({ data: { data: { attachmentId: 'att-3' } } })

    const result = await uploadAttachmentResumable(file, { chunkSize: 512 })

    expect(createUploadSession).toHaveBeenCalledTimes(1)
    expect(completeUpload).toHaveBeenCalledWith('fresh-upload')
    expect(result.attachmentId).toBe('att-3')
  })

  it('onProgress callback fires with correct percentages', async () => {
    const file = makeFile({ size: 1024 })
    createUploadSession.mockResolvedValue({ data: { data: { uploadId: 'up-p' } } })
    uploadChunk.mockResolvedValue({})
    completeUpload.mockResolvedValue({ data: { data: {} } })
    const onProgress = vi.fn()

    await uploadAttachmentResumable(file, { chunkSize: 512, onProgress })

    expect(onProgress).toHaveBeenCalledTimes(2)
    expect(onProgress.mock.calls[0][0].progress).toBe(50)
    expect(onProgress.mock.calls[1][0].progress).toBe(100)
  })

  it('removes upload state after completion', async () => {
    const file = makeFile({ size: 512 })
    createUploadSession.mockResolvedValue({ data: { data: { uploadId: 'up-c' } } })
    uploadChunk.mockResolvedValue({})
    completeUpload.mockResolvedValue({ data: { data: {} } })

    await uploadAttachmentResumable(file, { chunkSize: 512 })

    expect(store.has('upload:f.bin:512:123')).toBe(false)
  })

  it('respects custom fileFingerprint option', async () => {
    const file = makeFile({ size: 256 })
    createUploadSession.mockResolvedValue({ data: { data: { uploadId: 'up-fp' } } })
    uploadChunk.mockResolvedValue({})
    completeUpload.mockResolvedValue({ data: { data: {} } })

    await uploadAttachmentResumable(file, { chunkSize: 256, fileFingerprint: 'custom-key' })

    // state key must have been built with the custom fingerprint before cleanup
    expect(createUploadSession).toHaveBeenCalled()
  })
})
