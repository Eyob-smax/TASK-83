import { describe, it, expect, vi, beforeEach } from "vitest";
import { setActivePinia, createPinia } from "pinia";
import { useOfflineStore } from "../../src/stores/offline.js";

// Mock localforage
vi.mock("../../src/utils/offline.js", () => ({
  offlineQueue: {
    keys: vi.fn().mockResolvedValue([]),
    getItem: vi.fn().mockResolvedValue(null),
    setItem: vi.fn().mockResolvedValue(undefined),
    removeItem: vi.fn().mockResolvedValue(undefined),
  },
  rosterCache: {
    setItem: vi.fn().mockResolvedValue(undefined),
    getItem: vi.fn().mockResolvedValue(null),
    removeItem: vi.fn().mockResolvedValue(undefined),
  },
}));

vi.mock("@/utils/resumableUpload", () => ({
  uploadAttachmentResumable: vi
    .fn()
    .mockResolvedValue({ uploadId: "upload-1", completed: true }),
}));

describe("offline store", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it("initializes as online by default", () => {
    const store = useOfflineStore();
    expect(store.isOnline).toBe(true);
  });

  it("starts with empty pending actions", () => {
    const store = useOfflineStore();
    expect(store.pendingActions).toEqual([]);
  });

  it("syncInProgress starts false", () => {
    const store = useOfflineStore();
    expect(store.syncInProgress).toBe(false);
  });

  it("enqueuePendingAction adds to queue", async () => {
    const store = useOfflineStore();
    await store.enqueuePendingAction({
      method: "POST",
      url: "/api/checkin",
      data: { userId: "u1" },
    });
    expect(store.pendingActions).toHaveLength(1);
    expect(store.pendingActions[0].method).toBe("POST");
  });

  it("cacheRoster stores data", async () => {
    const store = useOfflineStore();
    await store.cacheRoster("session-1", [{ userId: "u1", name: "Alice" }]);
    // Verify rosterCache.setItem was called
    const { rosterCache } = await import("../../src/utils/offline.js");
    expect(rosterCache.setItem).toHaveBeenCalled();
  });

  it("getCachedRoster returns null when not cached", async () => {
    const store = useOfflineStore();
    const result = await store.getCachedRoster("no-session");
    expect(result).toBeNull();
  });

  it("uploadAttachment delegates to resumable uploader", async () => {
    const store = useOfflineStore();
    const file = new File(["hello"], "notes.txt", { type: "text/plain" });

    const result = await store.uploadAttachment(file);
    const { uploadAttachmentResumable } =
      await import("@/utils/resumableUpload");

    expect(uploadAttachmentResumable).toHaveBeenCalledWith(file, {});
    expect(result.completed).toBe(true);
  });

  it("initListeners registers online/offline handlers and flips isOnline", async () => {
    const store = useOfflineStore();
    store.initListeners();
    // Simulate an offline event then online event
    window.dispatchEvent(new Event("offline"));
    expect(store.isOnline).toBe(false);
    window.dispatchEvent(new Event("online"));
    expect(store.isOnline).toBe(true);
  });

  it("loadPendingActions populates state from queue", async () => {
    const { offlineQueue } = await import("../../src/utils/offline.js");
    offlineQueue.keys.mockResolvedValueOnce(["k1", "k2"]);
    offlineQueue.getItem.mockImplementation(async (k) => ({ id: k, method: "POST" }));
    const store = useOfflineStore();
    await store.loadPendingActions();
    expect(store.pendingActions).toHaveLength(2);
  });

  it("loadPendingActions swallows errors silently", async () => {
    const { offlineQueue } = await import("../../src/utils/offline.js");
    offlineQueue.keys.mockRejectedValueOnce(new Error("boom"));
    const store = useOfflineStore();
    await expect(store.loadPendingActions()).resolves.toBeUndefined();
  });

  it("replayPendingActions is a no-op when offline", async () => {
    const { offlineQueue } = await import("../../src/utils/offline.js");
    const store = useOfflineStore();
    store.isOnline = false;
    await store.replayPendingActions();
    expect(offlineQueue.keys).not.toHaveBeenCalled();
  });

  it("replayPendingActions is a no-op when syncInProgress", async () => {
    const { offlineQueue } = await import("../../src/utils/offline.js");
    const store = useOfflineStore();
    store.syncInProgress = true;
    await store.replayPendingActions();
    expect(offlineQueue.keys).not.toHaveBeenCalled();
  });

  it("dismissConflict removes conflict by key", () => {
    const store = useOfflineStore();
    store.conflictedActions.push({ key: "k1", code: "X", message: "y" });
    store.conflictedActions.push({ key: "k2", code: "X", message: "y" });
    store.dismissConflict("k1");
    expect(store.conflictedActions).toHaveLength(1);
    expect(store.conflictedActions[0].key).toBe("k2");
  });

  it("getCachedRoster returns data when cached and fresh", async () => {
    const { rosterCache } = await import("../../src/utils/offline.js");
    rosterCache.getItem.mockResolvedValueOnce({ data: [{ id: "u1" }], cachedAt: Date.now() });
    const store = useOfflineStore();
    const res = await store.getCachedRoster("s1");
    expect(res).toEqual([{ id: "u1" }]);
  });

  it("getCachedRoster returns null when cache is stale", async () => {
    const { rosterCache } = await import("../../src/utils/offline.js");
    rosterCache.getItem.mockResolvedValueOnce({ data: [{ id: "u1" }], cachedAt: 0 });
    const store = useOfflineStore();
    const res = await store.getCachedRoster("s1");
    expect(res).toBeNull();
    expect(rosterCache.removeItem).toHaveBeenCalledWith("s1");
  });
});
