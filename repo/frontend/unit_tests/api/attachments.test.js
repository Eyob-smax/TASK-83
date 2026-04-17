import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('../../src/api/client.js', () => ({
  default: {
    get: vi.fn(() => Promise.resolve({ data: {} })),
    post: vi.fn(() => Promise.resolve({ data: {} })),
    put: vi.fn(() => Promise.resolve({ data: {} }))
  }
}))

import apiClient from '../../src/api/client.js'
import {
  createUploadSession,
  uploadChunk,
  getUploadStatus,
  completeUpload
} from '../../src/api/attachments.js'

describe('api/attachments', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('createUploadSession posts metadata envelope', async () => {
    await createUploadSession('report.pdf', 1024, 2)
    expect(apiClient.post).toHaveBeenCalledWith('/attachments/sessions', {
      fileName: 'report.pdf',
      totalSize: 1024,
      totalChunks: 2
    })
  })

  it('uploadChunk PUTs blob with octet-stream content-type', async () => {
    const blob = new Blob(['chunk-data'])
    await uploadChunk('up-1', 0, blob)
    expect(apiClient.put).toHaveBeenCalledWith(
      '/attachments/sessions/up-1/chunks/0',
      blob,
      { headers: { 'Content-Type': 'application/octet-stream' } }
    )
  })

  it('getUploadStatus gets session info', async () => {
    await getUploadStatus('up-1')
    expect(apiClient.get).toHaveBeenCalledWith('/attachments/sessions/up-1')
  })

  it('completeUpload posts completion', async () => {
    await completeUpload('up-1')
    expect(apiClient.post).toHaveBeenCalledWith('/attachments/sessions/up-1/complete')
  })
})
