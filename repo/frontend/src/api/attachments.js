import apiClient from "./client";

export function createUploadSession(fileName, totalSize, totalChunks) {
  return apiClient.post("/attachments/sessions", {
    fileName,
    totalSize,
    totalChunks,
  });
}

export function uploadChunk(uploadId, chunkIndex, chunkBlob) {
  return apiClient.put(
    `/attachments/sessions/${uploadId}/chunks/${chunkIndex}`,
    chunkBlob,
    {
      headers: {
        "Content-Type": "application/octet-stream",
      },
    },
  );
}

export function getUploadStatus(uploadId) {
  return apiClient.get(`/attachments/sessions/${uploadId}`);
}

export function completeUpload(uploadId) {
  return apiClient.post(`/attachments/sessions/${uploadId}/complete`);
}
