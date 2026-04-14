<template>
  <div class="subscription-settings-view">
    <div class="page-header">
      <h1>Notification Preferences</h1>
      <router-link to="/notifications" class="back-link">
        Back to Notifications
      </router-link>
    </div>
    <p class="subtitle">
      Choose which notifications you receive and configure Do Not Disturb hours.
    </p>

    <ErrorAlert
      v-if="notifStore.error"
      :message="notifStore.error"
      title="Error"
      :retryable="true"
      :dismissable="true"
      @retry="loadAll"
      @dismiss="notifStore.error = null"
    />

    <LoadingSpinner v-if="initialLoading" message="Loading preferences..." />

    <template v-else>
      <!-- Subscription Toggles -->
      <section class="section">
        <h2>Notification Types</h2>
        <p class="section-hint">
          Enable or disable individual notification types. Disabled types will
          not generate in-app or email notifications.
        </p>

        <div class="toggle-list">
          <div
            v-for="ntype in notificationTypes"
            :key="ntype.value"
            class="toggle-row"
          >
            <div class="toggle-info">
              <span class="toggle-label">{{ ntype.label }}</span>
              <span class="toggle-description">{{ ntype.description }}</span>
            </div>
            <label class="toggle-switch">
              <input
                type="checkbox"
                :checked="isSubscribed(ntype.value)"
                @change="toggleSubscription(ntype.value)"
                :disabled="subsSaving"
              />
              <span class="toggle-slider"></span>
            </label>
          </div>
        </div>

        <!-- Subscriptions save -->
        <div class="action-row">
          <button
            class="btn-primary"
            :disabled="subsSaving || !subsChanged"
            @click="saveSubscriptions"
          >
            <span v-if="subsSaving" class="btn-spinner"></span>
            {{ subsSaving ? "Saving..." : "Save Subscriptions" }}
          </button>
          <span v-if="subsSuccess" class="save-feedback success"
            >Subscriptions saved</span
          >
          <span v-if="subsError" class="save-feedback error">{{
            subsError
          }}</span>
        </div>
      </section>

      <!-- DND Settings -->
      <section class="section">
        <h2>Do Not Disturb</h2>
        <p class="section-hint">
          During DND hours, notifications are queued silently and delivered
          afterward.
        </p>

        <div class="dnd-form">
          <div class="toggle-row">
            <div class="toggle-info">
              <span class="toggle-label">Enable Do Not Disturb</span>
              <span class="toggle-description">
                Suppress notification delivery during the configured time window
              </span>
            </div>
            <label class="toggle-switch">
              <input
                type="checkbox"
                v-model="dndEnabled"
                :disabled="dndSaving"
              />
              <span class="toggle-slider"></span>
            </label>
          </div>

          <div class="time-inputs" :class="{ disabled: !dndEnabled }">
            <div class="form-group">
              <label for="dnd-start">Start Time</label>
              <input
                id="dnd-start"
                type="time"
                v-model="dndStart"
                :disabled="!dndEnabled || dndSaving"
              />
            </div>
            <div class="form-group">
              <label for="dnd-end">End Time</label>
              <input
                id="dnd-end"
                type="time"
                v-model="dndEnd"
                :disabled="!dndEnabled || dndSaving"
              />
            </div>
          </div>

          <div class="dnd-preview" v-if="dndEnabled">
            Notifications will be silenced from
            <strong>{{ dndStart }}</strong> to <strong>{{ dndEnd }}</strong
            >.
          </div>
        </div>

        <!-- DND save -->
        <div class="action-row">
          <button
            class="btn-primary"
            :disabled="dndSaving || !dndChanged"
            @click="saveDnd"
          >
            <span v-if="dndSaving" class="btn-spinner"></span>
            {{ dndSaving ? "Saving..." : "Save DND Settings" }}
          </button>
          <span v-if="dndSuccess" class="save-feedback success"
            >DND settings saved</span
          >
          <span v-if="dndError" class="save-feedback error">{{
            dndError
          }}</span>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
import { useNotificationsStore } from "@/stores/notifications";
import { NotificationType } from "@/api/types";
import LoadingSpinner from "@/components/common/LoadingSpinner.vue";
import ErrorAlert from "@/components/common/ErrorAlert.vue";

const notifStore = useNotificationsStore();

// Notification type definitions
const notificationTypes = [
  {
    value: NotificationType.REGISTRATION_CONFIRMATION,
    label: "Registration Confirmation",
    description: "Confirmation when you successfully register for a session",
  },
  {
    value: NotificationType.WAITLIST_PROMOTION,
    label: "Waitlist Promotion",
    description:
      "Notification when you are promoted from the waitlist to a confirmed spot",
  },
  {
    value: NotificationType.WAITLIST_EXPIRED,
    label: "Waitlist Expired",
    description: "Notification when your waitlist position expires",
  },
  {
    value: NotificationType.CHECKIN_EXCEPTION,
    label: "Check-in Exception",
    description: "Alerts for check-in failures or denied check-in attempts",
  },
  {
    value: NotificationType.CHECKIN_DEVICE_WARNING,
    label: "Device Warning",
    description: "Warnings about multi-device or device conflict issues",
  },
  {
    value: NotificationType.FINANCE_POSTING_RESULT,
    label: "Finance Posting Result",
    description: "Results of financial posting operations",
  },
  {
    value: NotificationType.IMPORT_COMPLETED,
    label: "Import Completed",
    description: "Notification when a data import finishes successfully",
  },
  {
    value: NotificationType.IMPORT_FAILED,
    label: "Import Failed",
    description: "Notification when a data import encounters an error",
  },
  {
    value: NotificationType.SYSTEM_ALERT,
    label: "System Alert",
    description: "Critical system alerts and maintenance notifications",
  },
];

// State
const initialLoading = ref(true);
const localSubscriptions = ref(new Set());
const originalSubscriptions = ref(new Set());
const subsSaving = ref(false);
const subsSuccess = ref(false);
const subsError = ref("");

const dndEnabled = ref(false);
const dndStart = ref("21:00");
const dndEnd = ref("07:00");
const originalDnd = ref({ enabled: false, start: "21:00", end: "07:00" });
const dndSaving = ref(false);
const dndSuccess = ref(false);
const dndError = ref("");

let subsSuccessTimeout = null;
let dndSuccessTimeout = null;

const subsChanged = computed(() => {
  if (localSubscriptions.value.size !== originalSubscriptions.value.size)
    return true;
  for (const s of localSubscriptions.value) {
    if (!originalSubscriptions.value.has(s)) return true;
  }
  return false;
});

const dndChanged = computed(() => {
  return (
    dndEnabled.value !== originalDnd.value.enabled ||
    dndStart.value !== originalDnd.value.start ||
    dndEnd.value !== originalDnd.value.end
  );
});

onMounted(async () => {
  await loadAll();
});

async function loadAll() {
  initialLoading.value = true;
  try {
    await Promise.all([
      notifStore.fetchSubscriptions(),
      notifStore.fetchDndSettings(),
    ]);

    // Initialize subscriptions from store
    const subs = notifStore.subscriptions;
    if (Array.isArray(subs)) {
      // If the store returns an array of type strings or objects with .type / .notificationType
      const enabledTypes = subs
        .filter((s) => {
          if (typeof s === "string") return true;
          return s.enabled !== false;
        })
        .map((s) => {
          if (typeof s === "string") return s;
          return s.type || s.notificationType || s;
        });
      localSubscriptions.value = new Set(enabledTypes);
      originalSubscriptions.value = new Set(enabledTypes);
    }

    // Initialize DND from store
    const dnd = notifStore.dndSettings;
    if (dnd) {
      dndEnabled.value = dnd.enabled ?? false;
      dndStart.value = dnd.startTime || dnd.start || "21:00";
      dndEnd.value = dnd.endTime || dnd.end || "07:00";
      originalDnd.value = {
        enabled: dndEnabled.value,
        start: dndStart.value,
        end: dndEnd.value,
      };
    }
  } finally {
    initialLoading.value = false;
  }
}

function isSubscribed(type) {
  return localSubscriptions.value.has(type);
}

function toggleSubscription(type) {
  subsSuccess.value = false;
  subsError.value = "";
  if (localSubscriptions.value.has(type)) {
    localSubscriptions.value.delete(type);
  } else {
    localSubscriptions.value.add(type);
  }
  // Force reactivity
  localSubscriptions.value = new Set(localSubscriptions.value);
}

async function saveSubscriptions() {
  subsSaving.value = true;
  subsSuccess.value = false;
  subsError.value = "";

  try {
    // Build payload: array of objects matching backend expectation
    const payload = notificationTypes.map((nt) => ({
      notificationType: nt.value,
      enabled: localSubscriptions.value.has(nt.value),
    }));

    await notifStore.updateSubscriptions(payload);
    originalSubscriptions.value = new Set(localSubscriptions.value);
    subsSuccess.value = true;

    if (subsSuccessTimeout) clearTimeout(subsSuccessTimeout);
    subsSuccessTimeout = setTimeout(() => {
      subsSuccess.value = false;
    }, 4000);
  } catch (e) {
    subsError.value =
      e.response?.data?.message || "Failed to save subscriptions";
  } finally {
    subsSaving.value = false;
  }
}

async function saveDnd() {
  dndSaving.value = true;
  dndSuccess.value = false;
  dndError.value = "";

  try {
    await notifStore.updateDnd({
      enabled: dndEnabled.value,
      startTime: dndStart.value,
      endTime: dndEnd.value,
    });

    originalDnd.value = {
      enabled: dndEnabled.value,
      start: dndStart.value,
      end: dndEnd.value,
    };
    dndSuccess.value = true;

    if (dndSuccessTimeout) clearTimeout(dndSuccessTimeout);
    dndSuccessTimeout = setTimeout(() => {
      dndSuccess.value = false;
    }, 4000);
  } catch (e) {
    dndError.value = e.response?.data?.message || "Failed to save DND settings";
  } finally {
    dndSaving.value = false;
  }
}
</script>

<style scoped>
.subscription-settings-view {
  max-width: 720px;
  margin: 0 auto;
  padding: 24px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.page-header h1 {
  margin: 0;
  font-size: 1.75rem;
  color: #1a237e;
}

.back-link {
  color: #1a237e;
  text-decoration: none;
  font-size: 0.9rem;
  font-weight: 600;
}

.back-link:hover {
  text-decoration: underline;
}

.subtitle {
  margin: 0 0 24px;
  color: #666;
  font-size: 0.9rem;
}

.section {
  margin-bottom: 36px;
}

.section h2 {
  font-size: 1.15rem;
  color: #333;
  margin: 0 0 6px;
  padding-bottom: 6px;
  border-bottom: 1px solid #e0e0e0;
}

.section-hint {
  margin: 0 0 16px;
  color: #888;
  font-size: 0.85rem;
}

/* Toggle list */
.toggle-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.toggle-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 16px;
  background: #fff;
  border: 1px solid #e0e0e0;
  border-radius: 4px;
}

.toggle-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}

.toggle-label {
  font-size: 0.95rem;
  font-weight: 600;
  color: #333;
}

.toggle-description {
  font-size: 0.8rem;
  color: #888;
}

/* Toggle switch */
.toggle-switch {
  position: relative;
  display: inline-block;
  width: 44px;
  height: 24px;
  flex-shrink: 0;
  cursor: pointer;
}

.toggle-switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.toggle-slider {
  position: absolute;
  inset: 0;
  background: #ccc;
  border-radius: 24px;
  transition: background 0.2s;
}

.toggle-slider::before {
  content: "";
  position: absolute;
  left: 2px;
  bottom: 2px;
  width: 20px;
  height: 20px;
  background: #fff;
  border-radius: 50%;
  transition: transform 0.2s;
}

.toggle-switch input:checked + .toggle-slider {
  background: #1a237e;
}

.toggle-switch input:checked + .toggle-slider::before {
  transform: translateX(20px);
}

.toggle-switch input:disabled + .toggle-slider {
  opacity: 0.5;
  cursor: not-allowed;
}

/* DND form */
.dnd-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.time-inputs {
  display: flex;
  gap: 20px;
  transition: opacity 0.2s;
}

.time-inputs.disabled {
  opacity: 0.45;
  pointer-events: none;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.form-group label {
  font-weight: 600;
  font-size: 0.875rem;
  color: #333;
}

.form-group input[type="time"] {
  padding: 10px 12px;
  border: 1px solid #ccc;
  border-radius: 4px;
  font-size: 0.95rem;
  width: 160px;
}

.form-group input[type="time"]:focus {
  outline: none;
  border-color: #1a237e;
  box-shadow: 0 0 0 2px rgba(26, 35, 126, 0.15);
}

.form-group input[type="time"]:disabled {
  background: #f5f5f5;
  cursor: not-allowed;
}

.dnd-preview {
  font-size: 0.85rem;
  color: #555;
  background: #e8eaf6;
  padding: 10px 14px;
  border-radius: 4px;
}

.dnd-preview strong {
  color: #1a237e;
}

/* Action row */
.action-row {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 16px;
}

.btn-primary {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 10px 20px;
  background: #1a237e;
  color: #fff;
  border: none;
  border-radius: 4px;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s;
}

.btn-primary:hover:not(:disabled) {
  background: #283593;
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.4);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.save-feedback {
  font-size: 0.875rem;
  font-weight: 600;
}

.save-feedback.success {
  color: #2e7d32;
}

.save-feedback.error {
  color: #c62828;
}
</style>
