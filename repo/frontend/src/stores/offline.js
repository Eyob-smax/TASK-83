import { defineStore } from "pinia";
import { ref } from "vue";
import { offlineQueue, rosterCache } from "@/utils/offline";
import { uploadAttachmentResumable } from "@/utils/resumableUpload";

export const useOfflineStore = defineStore("offline", () => {
  const isOnline = ref(
    typeof navigator !== "undefined" ? navigator.onLine : true,
  );
  const pendingActions = ref([]);
  const syncInProgress = ref(false);
  const conflictedActions = ref([]);

  function initListeners() {
    window.addEventListener("online", () => {
      isOnline.value = true;
    });
    window.addEventListener("offline", () => {
      isOnline.value = false;
    });
    loadPendingActions();
  }

  async function loadPendingActions() {
    try {
      const keys = await offlineQueue.keys();
      const actions = [];
      for (const key of keys) {
        actions.push(await offlineQueue.getItem(key));
      }
      pendingActions.value = actions.filter(Boolean);
    } catch (e) {
      /* silent */
    }
  }

  async function enqueuePendingAction(action) {
    const id = Date.now() + "_" + Math.random().toString(36).substr(2, 9);
    const entry = { id, ...action, queuedAt: new Date().toISOString() };
    await offlineQueue.setItem(id, entry);
    pendingActions.value.push(entry);
  }

  async function replayPendingActions() {
    if (syncInProgress.value || !isOnline.value) return;
    syncInProgress.value = true;
    const keys = await offlineQueue.keys();
    for (const key of keys) {
      const action = await offlineQueue.getItem(key);
      if (!action) continue;
      try {
        const { default: apiClient } = await import("@/api/client");
        await apiClient({
          method: action.method,
          url: action.url,
          data: action.data,
        });
        await offlineQueue.removeItem(key);
      } catch (e) {
        if (e.response?.status === 409) {
          const code = e.response?.data?.errors?.[0]?.code || "CONFLICT";
          const message =
            e.response?.data?.message ||
            "Action could not be applied due to a conflict";
          conflictedActions.value.push({ key, action, code, message });
          await offlineQueue.removeItem(key);
        }
        break;
      }
    }
    await loadPendingActions();
    syncInProgress.value = false;
  }

  function dismissConflict(key) {
    conflictedActions.value = conflictedActions.value.filter(
      (c) => c.key !== key,
    );
  }

  async function cacheRoster(sessionId, data) {
    await rosterCache.setItem(sessionId, { data, cachedAt: Date.now() });
  }

  async function getCachedRoster(sessionId) {
    const entry = await rosterCache.getItem(sessionId);
    if (!entry) return null;
    if (Date.now() - entry.cachedAt > 5 * 60 * 1000) {
      await rosterCache.removeItem(sessionId);
      return null;
    }
    return entry.data;
  }

  async function uploadAttachment(file, options = {}) {
    return uploadAttachmentResumable(file, options);
  }

  return {
    isOnline,
    pendingActions,
    syncInProgress,
    conflictedActions,
    initListeners,
    enqueuePendingAction,
    replayPendingActions,
    dismissConflict,
    cacheRoster,
    getCachedRoster,
    loadPendingActions,
    uploadAttachment,
  };
});
