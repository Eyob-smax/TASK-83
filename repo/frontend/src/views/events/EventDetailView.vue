<template>
  <div class="event-detail-view">
    <LoadingSpinner
      v-if="eventsStore.loading && !eventsStore.currentEvent"
      message="Loading session details..."
    />

    <ErrorAlert
      v-else-if="eventsStore.error"
      :message="eventsStore.error"
      title="Failed to Load"
      :retryable="true"
      @retry="loadEvent"
    />

    <template v-else-if="event">
      <div class="detail-header">
        <router-link to="/" class="back-link"
          >&larr; Back to Sessions</router-link
        >
        <h1>{{ event.title }}</h1>
        <span
          class="status-tag"
          :class="'status-' + (event.status || '').toLowerCase()"
        >
          {{ formatStatus(event.status) }}
        </span>
      </div>

      <div class="detail-grid">
        <section class="info-section">
          <h2>Session Details</h2>
          <div class="info-row">
            <span class="info-label">Description</span>
            <p class="info-value">
              {{ event.description || "No description provided." }}
            </p>
          </div>
          <div class="info-row">
            <span class="info-label">Location</span>
            <span class="info-value">{{ event.location || "TBD" }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">Start</span>
            <span class="info-value">{{
              formatDateTime(event.startTime)
            }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">End</span>
            <span class="info-value">{{ formatDateTime(event.endTime) }}</span>
          </div>
          <div v-if="event.deviceBindingRequired" class="device-notice">
            Device binding is required for check-in to this session. You must
            use the same device you register from.
          </div>
          <div v-if="event.waitlistPromotionCutoff" class="info-row">
            <span class="info-label">Waitlist Cutoff</span>
            <span class="info-value cutoff-date">{{
              formatDateTime(event.waitlistPromotionCutoff)
            }}</span>
          </div>
        </section>

        <section class="capacity-section">
          <h2>Availability</h2>
          <div class="capacity-bar-container">
            <div class="capacity-bar">
              <div
                class="capacity-fill"
                :style="{ width: capacityPercent + '%' }"
                :class="capacityColorClass"
              ></div>
            </div>
            <div class="capacity-numbers">
              <span :class="seatCountClass"
                >{{ event.remainingSeats ?? "—" }} seats remaining</span
              >
              <span class="capacity-total"
                >of {{ event.maxCapacity ?? "—" }} total</span
              >
            </div>
          </div>
          <p class="refresh-note">
            Seat count refreshes automatically every 10 seconds.
          </p>

          <div class="action-area">
            <ErrorAlert
              v-if="registrationStore.error"
              :message="registrationStore.error"
              title="Registration Error"
              :dismissable="true"
              @dismiss="registrationStore.error = null"
            />

            <div
              v-if="registrationStore.conflictMessage"
              class="conflict-alert"
              role="alert"
            >
              <strong>Already Registered</strong>
              <p>{{ registrationStore.conflictMessage }}</p>
              <button
                class="btn-dismiss"
                @click="registrationStore.clearConflict()"
              >
                Dismiss
              </button>
            </div>

            <div v-if="successResult" class="success-alert" role="status">
              <strong>Registration Successful</strong>
              <p>
                Your status:
                <span
                  class="result-status"
                  :class="'rs-' + successResult.status?.toLowerCase()"
                  >{{ successResult.status }}</span
                >
              </p>
              <p v-if="successResult.status === 'WAITLISTED'">
                You have been added to the waitlist. You will be notified if a
                spot opens up.
              </p>
              <p v-else>You are confirmed for this session.</p>
            </div>

            <button
              v-if="!successResult"
              class="btn-register"
              :disabled="!canRegister || registrationStore.loading"
              @click="handleRegister"
            >
              <span v-if="registrationStore.loading" class="btn-spinner"></span>
              {{ registerButtonText }}
            </button>
          </div>
        </section>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from "vue";
import { useRoute } from "vue-router";
import { useEventsStore } from "@/stores/events";
import { useRegistrationStore } from "@/stores/registration";
import LoadingSpinner from "@/components/common/LoadingSpinner.vue";
import ErrorAlert from "@/components/common/ErrorAlert.vue";

const route = useRoute();
const eventsStore = useEventsStore();
const registrationStore = useRegistrationStore();

const successResult = ref(null);
let refreshInterval = null;

const event = computed(() => eventsStore.currentEvent);

const capacityPercent = computed(() => {
  if (
    !event.value ||
    event.value.maxCapacity == null ||
    event.value.remainingSeats == null
  )
    return 0;
  const filled = event.value.maxCapacity - event.value.remainingSeats;
  return Math.min(100, Math.round((filled / event.value.maxCapacity) * 100));
});

const capacityColorClass = computed(() => {
  if (
    !event.value ||
    event.value.maxCapacity == null ||
    event.value.remainingSeats == null
  )
    return "";
  const ratio = event.value.remainingSeats / event.value.maxCapacity;
  if (ratio > 0.5) return "fill-green";
  if (ratio > 0) return "fill-yellow";
  return "fill-red";
});

const seatCountClass = computed(() => {
  if (
    !event.value ||
    event.value.maxCapacity == null ||
    event.value.remainingSeats == null
  )
    return "";
  const ratio = event.value.remainingSeats / event.value.maxCapacity;
  if (ratio > 0.5) return "seats-green";
  if (ratio > 0) return "seats-yellow";
  return "seats-red";
});

const canRegister = computed(() => {
  if (!event.value) return false;
  if (successResult.value) return false;
  const status = event.value.status;
  return status === "OPEN_FOR_REGISTRATION" || status === "FULL";
});

const registerButtonText = computed(() => {
  if (registrationStore.loading) return "Registering...";
  if (!event.value) return "Register";
  if (event.value.status === "FULL") return "Join Waitlist";
  if (event.value.status !== "OPEN_FOR_REGISTRATION")
    return "Registration Not Open";
  return "Register";
});

function loadEvent() {
  const id = route.params.id;
  eventsStore.fetchEvent(id);
}

function startAutoRefresh() {
  refreshInterval = setInterval(() => {
    const id = route.params.id;
    if (id) {
      eventsStore.fetchAvailability(id);
    }
  }, 10000);
}

async function handleRegister() {
  if (!canRegister.value) return;

  registrationStore.error = null;
  registrationStore.clearConflict();

  try {
    const result = await registrationStore.registerForSession(event.value.id);
    successResult.value = result;
    eventsStore.fetchAvailability(event.value.id);
  } catch {
    // Error state is handled by the store
  }
}

function formatStatus(status) {
  if (!status) return "";
  return status.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
}

function formatDateTime(iso) {
  if (!iso) return "—";
  try {
    const d = new Date(iso);
    return d.toLocaleString(undefined, {
      year: "numeric",
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return iso;
  }
}

onMounted(() => {
  loadEvent();
  startAutoRefresh();
});

onUnmounted(() => {
  if (refreshInterval) clearInterval(refreshInterval);
  registrationStore.clearConflict();
  registrationStore.error = null;
});
</script>

<style scoped>
.event-detail-view {
  max-width: 900px;
  margin: 0 auto;
  padding: 24px;
}

.back-link {
  display: inline-block;
  margin-bottom: 8px;
  color: #1a237e;
  text-decoration: none;
  font-size: 0.875rem;
  font-weight: 600;
}

.back-link:hover {
  text-decoration: underline;
}

.detail-header {
  margin-bottom: 24px;
}

.detail-header h1 {
  margin: 0 0 8px;
  color: #1a237e;
}

.status-tag {
  display: inline-block;
  padding: 3px 12px;
  border-radius: 12px;
  font-size: 0.8rem;
  font-weight: 600;
  text-transform: uppercase;
}

.status-open_for_registration {
  background: #e8f5e9;
  color: #2e7d32;
}
.status-published {
  background: #e3f2fd;
  color: #1565c0;
}
.status-draft {
  background: #f5f5f5;
  color: #757575;
}
.status-full {
  background: #fff3e0;
  color: #e65100;
}
.status-in_progress {
  background: #e8eaf6;
  color: #283593;
}
.status-completed {
  background: #f5f5f5;
  color: #616161;
}
.status-cancelled {
  background: #fce4ec;
  color: #c62828;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}

@media (max-width: 700px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
}

.info-section,
.capacity-section {
  background: #fff;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 24px;
}

.info-section h2,
.capacity-section h2 {
  margin: 0 0 16px;
  font-size: 1.1rem;
  color: #333;
}

.info-row {
  margin-bottom: 12px;
}

.info-label {
  display: block;
  font-size: 0.8rem;
  font-weight: 600;
  color: #888;
  text-transform: uppercase;
  margin-bottom: 2px;
}

.info-value {
  font-size: 0.95rem;
  color: #333;
  margin: 0;
}

.device-notice {
  background: #fff8e1;
  border: 1px solid #ffe082;
  border-radius: 4px;
  padding: 10px 14px;
  font-size: 0.85rem;
  color: #f57f17;
  margin: 12px 0;
}

.cutoff-date {
  color: #e65100;
  font-weight: 600;
}

.capacity-bar-container {
  margin-bottom: 16px;
}

.capacity-bar {
  width: 100%;
  height: 16px;
  background: #e0e0e0;
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 8px;
}

.capacity-fill {
  height: 100%;
  border-radius: 8px;
  transition: width 0.5s ease;
}

.fill-green {
  background: #4caf50;
}
.fill-yellow {
  background: #ff9800;
}
.fill-red {
  background: #f44336;
}

.capacity-numbers {
  display: flex;
  justify-content: space-between;
  font-size: 0.875rem;
}

.seats-green {
  color: #2e7d32;
  font-weight: 600;
}
.seats-yellow {
  color: #f57f17;
  font-weight: 600;
}
.seats-red {
  color: #c62828;
  font-weight: 600;
}
.capacity-total {
  color: #888;
}

.refresh-note {
  font-size: 0.75rem;
  color: #999;
  margin: 0 0 20px;
}

.action-area {
  margin-top: 8px;
}

.conflict-alert {
  background: #fff3e0;
  border: 1px solid #ffcc80;
  border-radius: 4px;
  padding: 12px 16px;
  margin-bottom: 12px;
  color: #e65100;
}

.conflict-alert p {
  margin: 4px 0 8px;
}

.btn-dismiss {
  padding: 4px 12px;
  border: 1px solid #e65100;
  background: white;
  color: #e65100;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.8rem;
}

.success-alert {
  background: #e8f5e9;
  border: 1px solid #a5d6a7;
  border-radius: 4px;
  padding: 12px 16px;
  margin-bottom: 12px;
  color: #2e7d32;
}

.success-alert p {
  margin: 4px 0 0;
}

.result-status {
  font-weight: 700;
}

.rs-confirmed {
  color: #2e7d32;
}
.rs-waitlisted {
  color: #f57f17;
}

.btn-register {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  padding: 12px;
  background: #1a237e;
  color: #fff;
  border: none;
  border-radius: 4px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s;
}

.btn-register:hover:not(:disabled) {
  background: #283593;
}

.btn-register:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-spinner {
  width: 16px;
  height: 16px;
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
</style>
