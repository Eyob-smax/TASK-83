<template>
  <div class="event-list-view">
    <h1>Event Sessions</h1>

    <div class="toolbar">
      <div class="search-box">
        <input
          v-model.trim="searchQuery"
          type="text"
          placeholder="Search sessions..."
          @input="debouncedFetch"
        />
      </div>
      <div class="filter-box">
        <select v-model="statusFilter" @change="handleFilterChange">
          <option value="">All Statuses</option>
          <option v-for="status in statusOptions" :key="status" :value="status">
            {{ formatStatus(status) }}
          </option>
        </select>
      </div>
    </div>

    <LoadingSpinner v-if="eventsStore.loading" message="Loading sessions..." />

    <ErrorAlert
      v-else-if="eventsStore.error"
      :message="eventsStore.error"
      title="Failed to Load"
      :retryable="true"
      @retry="loadEvents"
    />

    <div v-else-if="eventsStore.events.length === 0" class="empty-state">
      <div class="empty-icon">&#128197;</div>
      <h2>No sessions found</h2>
      <p v-if="searchQuery || statusFilter">
        Try adjusting your search or filter criteria.
      </p>
      <p v-else>There are no event sessions available at this time.</p>
    </div>

    <div v-else class="event-grid">
      <div
        v-for="event in eventsStore.events"
        :key="event.id"
        class="event-card"
        @click="goToEvent(event.id)"
        role="button"
        tabindex="0"
        @keydown.enter="goToEvent(event.id)"
      >
        <div class="card-header">
          <h3 class="card-title">{{ event.title }}</h3>
          <span v-if="event.remainingSeats === 0" class="badge badge-waitlist"
            >Session Full - Join Waitlist</span
          >
        </div>
        <div class="card-body">
          <div class="card-detail">
            <span class="detail-label">Location:</span>
            <span>{{ event.location || "TBD" }}</span>
          </div>
          <div class="card-detail">
            <span class="detail-label">Date:</span>
            <span>{{ formatDateTime(event.startTime) }}</span>
          </div>
          <div class="card-detail">
            <span class="detail-label">Seats:</span>
            <span :class="seatClass(event)" class="seat-count">
              {{ event.remainingSeats ?? "—" }} / {{ event.maxCapacity ?? "—" }}
            </span>
          </div>
        </div>
        <div class="card-footer">
          <span
            class="status-tag"
            :class="'status-' + (event.status || '').toLowerCase()"
          >
            {{ formatStatus(event.status) }}
          </span>
        </div>
      </div>
    </div>

    <div
      v-if="!eventsStore.loading && eventsStore.events.length > 0"
      class="pagination"
    >
      <button
        class="btn-page"
        :disabled="currentPage <= 0"
        @click="goToPage(currentPage - 1)"
      >
        Previous
      </button>
      <span class="page-info">
        Page {{ currentPage + 1 }} of
        {{ eventsStore.pagination.totalPages || 1 }}
      </span>
      <button
        class="btn-page"
        :disabled="currentPage >= eventsStore.pagination.totalPages - 1"
        @click="goToPage(currentPage + 1)"
      >
        Next
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import { useRouter } from "vue-router";
import { useEventsStore } from "@/stores/events";
import { SessionStatus } from "@/api/types";
import LoadingSpinner from "@/components/common/LoadingSpinner.vue";
import ErrorAlert from "@/components/common/ErrorAlert.vue";

const router = useRouter();
const eventsStore = useEventsStore();

const searchQuery = ref("");
const statusFilter = ref("");
const currentPage = ref(0);

const statusOptions = Object.values(SessionStatus);

let debounceTimer = null;

function debouncedFetch() {
  clearTimeout(debounceTimer);
  debounceTimer = setTimeout(() => {
    currentPage.value = 0;
    loadEvents();
  }, 350);
}

function handleFilterChange() {
  currentPage.value = 0;
  loadEvents();
}

function loadEvents() {
  const params = { page: currentPage.value };
  if (searchQuery.value) params.search = searchQuery.value;
  if (statusFilter.value) params.status = statusFilter.value;
  eventsStore.fetchEvents(params);
}

function goToPage(page) {
  currentPage.value = page;
  loadEvents();
}

function goToEvent(id) {
  router.push(`/events/${id}`);
}

function seatClass(event) {
  if (event.remainingSeats == null || event.maxCapacity == null) return "";
  const ratio = event.remainingSeats / event.maxCapacity;
  if (ratio > 0.5) return "seats-green";
  if (ratio > 0) return "seats-yellow";
  return "seats-red";
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
  loadEvents();
});
</script>

<style scoped>
.event-list-view {
  max-width: 1000px;
  margin: 0 auto;
  padding: 24px;
}

.event-list-view h1 {
  margin: 0 0 20px;
  color: #1a237e;
}

.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.search-box {
  flex: 1;
  min-width: 200px;
}

.search-box input {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #ccc;
  border-radius: 4px;
  font-size: 0.95rem;
  box-sizing: border-box;
}

.search-box input:focus {
  outline: none;
  border-color: #1a237e;
  box-shadow: 0 0 0 2px rgba(26, 35, 126, 0.15);
}

.filter-box select {
  padding: 10px 12px;
  border: 1px solid #ccc;
  border-radius: 4px;
  font-size: 0.95rem;
  background: #fff;
  min-width: 180px;
}

.filter-box select:focus {
  outline: none;
  border-color: #1a237e;
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

.event-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}

.event-card {
  background: #fff;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 20px;
  cursor: pointer;
  transition:
    box-shadow 0.2s,
    transform 0.15s;
}

.event-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

.event-card:focus {
  outline: 2px solid #1a237e;
  outline-offset: 2px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 12px;
}

.card-title {
  margin: 0;
  font-size: 1.1rem;
  color: #1a237e;
}

.badge-waitlist {
  background: #f44336;
  color: #fff;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 0.7rem;
  font-weight: 600;
  white-space: nowrap;
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 12px;
}

.card-detail {
  font-size: 0.875rem;
  color: #444;
}

.detail-label {
  font-weight: 600;
  margin-right: 4px;
}

.seat-count {
  font-weight: 600;
}

.seats-green {
  color: #2e7d32;
}

.seats-yellow {
  color: #f57f17;
}

.seats-red {
  color: #c62828;
}

.card-footer {
  display: flex;
  justify-content: flex-end;
}

.status-tag {
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 0.75rem;
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

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  margin-top: 24px;
  padding: 16px 0;
}

.btn-page {
  padding: 8px 16px;
  border: 1px solid #ccc;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
  font-size: 0.875rem;
  color: #1a237e;
  font-weight: 600;
  transition: background 0.2s;
}

.btn-page:hover:not(:disabled) {
  background: #e8eaf6;
}

.btn-page:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-info {
  font-size: 0.875rem;
  color: #666;
}
</style>
