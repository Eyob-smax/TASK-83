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
});
