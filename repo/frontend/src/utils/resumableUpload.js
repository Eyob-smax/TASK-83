import localforage from "localforage";
import {
  completeUpload,
  createUploadSession,
  getUploadStatus,
  uploadChunk,
} from "@/api/attachments";

const uploadStateStore = localforage.createInstance({
  name: "eventops",
  storeName: "attachment_upload_state",
});

function buildStateKey(fileFingerprint) {
  return `upload:${fileFingerprint}`;
}

function buildFileFingerprint(file) {
  return `${file.name}:${file.size}:${file.lastModified}`;
}

export async function uploadAttachmentResumable(file, options = {}) {
  const chunkSize = options.chunkSize || 512 * 1024;
  const totalChunks = Math.max(1, Math.ceil(file.size / chunkSize));
  const fileFingerprint = options.fileFingerprint || buildFileFingerprint(file);
  const stateKey = buildStateKey(fileFingerprint);

  let state = await uploadStateStore.getItem(stateKey);

  if (!state) {
    const response = await createUploadSession(
      file.name,
      file.size,
      totalChunks,
    );
    state = {
      uploadId: response.data.data.uploadId,
      uploadedChunks: [],
    };
    await uploadStateStore.setItem(stateKey, state);
  } else {
    try {
      const statusResponse = await getUploadStatus(state.uploadId);
      state.uploadedChunks = statusResponse.data.data.uploadedChunks || [];
      await uploadStateStore.setItem(stateKey, state);
    } catch {
      const response = await createUploadSession(
        file.name,
        file.size,
        totalChunks,
      );
      state = {
        uploadId: response.data.data.uploadId,
        uploadedChunks: [],
      };
      await uploadStateStore.setItem(stateKey, state);
    }
  }

  for (let index = 0; index < totalChunks; index += 1) {
    if (state.uploadedChunks.includes(index)) {
      continue;
    }

    const start = index * chunkSize;
    const end = Math.min(file.size, start + chunkSize);
    const chunk = file.slice(start, end);

    await uploadChunk(state.uploadId, index, chunk);
    state.uploadedChunks.push(index);
    await uploadStateStore.setItem(stateKey, state);

    if (typeof options.onProgress === "function") {
      options.onProgress({
        uploadId: state.uploadId,
        uploadedChunks: state.uploadedChunks.length,
        totalChunks,
        progress: Math.round((state.uploadedChunks.length / totalChunks) * 100),
      });
    }
  }

  const completeResponse = await completeUpload(state.uploadId);
  await uploadStateStore.removeItem(stateKey);
  return completeResponse.data.data;
}
