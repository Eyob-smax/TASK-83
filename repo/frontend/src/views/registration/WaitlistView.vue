<template>
  <div class="waitlist-view">
    <h1>My Waitlist</h1>

    <LoadingSpinner
      v-if="registrationStore.loading && entries.length === 0"
      message="Loading waitlist..."
    />

    <ErrorAlert
      v-else-if="registrationStore.error"
      :message="registrationStore.error"
      title="Failed to Load"
      :retryable="true"
      @retry="loadWaitlist"
      @dismiss="registrationStore.error = null"
    />

    <div v-else-if="entries.length === 0" class="empty-state">
      <div class="empty-icon">&#128221;</div>
      <h2>You have no waitlist entries</h2>
      <p>
        When you join a waitlist for a full session, your entries will appear
        here.
      </p>
      <router-link to="/" class="btn-browse">Browse Sessions</router-link>
    </div>

    <div v-else class="waitlist-list">
      <div v-for="entry in entries" :key="entry.id" class="waitlist-card">
        <div class="card-left">
          <div class="position-badge">
            <span class="position-number">{{
              entry.waitlistPosition ?? entry.position ?? "—"
            }}</span>
            <span class="position-label">Position</span>
          </div>
        </div>
        <div class="card-center">
          <h3 class="session-title">
            {{
              entry.sessionTitle ||
              entry.eventTitle ||
              "Session #" + entry.sessionId
            }}
          </h3>
          <span class="status-badge badge-waitlisted">WAITLISTED</span>
          <div
            class="cutoff-row"
            :class="{ 'cutoff-warning': isCutoffNear(entry.promotedAt) }"
          >
            <span class="cutoff-label">Promoted At:</span>
            <span class="cutoff-value">{{
              formatDateTime(entry.promotedAt)
            }}</span>
          </div>
          <div v-if="entry.createdAt" class="meta-row">
            <span>Joined: {{ formatDateTime(entry.createdAt) }}</span>
          </div>
        </div>
        <div class="card-right">
          <router-link :to="'/events/' + entry.sessionId" class="btn-view"
            >View Session</router-link
          >
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted } from "vue";
import { useRegistrationStore } from "@/stores/registration";
import LoadingSpinner from "@/components/common/LoadingSpinner.vue";
import ErrorAlert from "@/components/common/ErrorAlert.vue";

const registrationStore = useRegistrationStore();

const entries = computed(() => registrationStore.waitlistEntries);

function loadWaitlist() {
  registrationStore.fetchWaitlist();
}

function isCutoffNear(cutoffIso) {
  if (!cutoffIso) return false;
  try {
    const cutoff = new Date(cutoffIso).getTime();
    const now = Date.now();
    const hoursRemaining = (cutoff - now) / (1000 * 60 * 60);
    return hoursRemaining >= 0 && hoursRemaining < 24;
  } catch {
    return false;
  }
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
  loadWaitlist();
});
</script>

<style scoped>
.waitlist-view {
  max-width: 800px;
  margin: 0 auto;
  padding: 24px;
}

.waitlist-view h1 {
  margin: 0 0 20px;
  color: #1a237e;
}

.empty-state {
  text-align: center;
  padding: 60px 24px;
  color: #666;
}

.empty-icon {
  font-size: 3rem;
  margin-bottom: 12px;
}

.empty-state h2 {
  margin: 0 0 8px;
  color: #333;
}

.btn-browse {
  display: inline-block;
  margin-top: 16px;
  padding: 10px 20px;
  background: #1a237e;
  color: #fff;
  border-radius: 4px;
  text-decoration: none;
  font-weight: 600;
  font-size: 0.9rem;
}

.btn-browse:hover {
  background: #283593;
}

.waitlist-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.waitlist-card {
  display: flex;
  gap: 16px;
  align-items: center;
  background: #fff;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 20px;
  flex-wrap: wrap;
}

.card-left {
  flex-shrink: 0;
}

.position-badge {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 60px;
  height: 60px;
  background: #e8eaf6;
  border-radius: 50%;
}

.position-number {
  font-size: 1.4rem;
  font-weight: 700;
  color: #1a237e;
  line-height: 1;
}

.position-label {
  font-size: 0.6rem;
  color: #5c6bc0;
  text-transform: uppercase;
  font-weight: 600;
}

.card-center {
  flex: 1;
  min-width: 0;
}

.session-title {
  margin: 0 0 6px;
  font-size: 1.05rem;
  color: #333;
}

.status-badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 0.7rem;
  font-weight: 700;
  text-transform: uppercase;
  margin-bottom: 8px;
}

.badge-waitlisted {
  background: #fff8e1;
  color: #f57f17;
}

.cutoff-row {
  font-size: 0.875rem;
  margin-bottom: 4px;
}

.cutoff-label {
  font-weight: 600;
  margin-right: 4px;
}

.cutoff-value {
  color: #555;
}

.cutoff-warning {
  color: #e65100;
  font-weight: 600;
}

.cutoff-warning .cutoff-value {
  color: #e65100;
}

.meta-row {
  font-size: 0.8rem;
  color: #999;
}

.card-right {
  flex-shrink: 0;
}

.btn-view {
  display: inline-block;
  padding: 8px 16px;
  border: 1px solid #1a237e;
  background: #fff;
  color: #1a237e;
  border-radius: 4px;
  text-decoration: none;
  font-size: 0.825rem;
  font-weight: 600;
  transition: background 0.2s;
}

.btn-view:hover {
  background: #e8eaf6;
}
</style>
