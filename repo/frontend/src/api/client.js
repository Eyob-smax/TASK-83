/**
 * Central axios instance for all backend API communication.
 *
 * Interceptors handle: auth headers, request signatures,
 * error normalization, and offline detection.
 */
import axios from "axios";
import {
  generateNonce,
  generateTimestamp,
  computeSignature,
} from "@/utils/signature";

const SESSION_SIGNATURE_TOKEN_KEY = "eventops_token";
const SESSION_USER_KEY = "eventops_user";
const UNSIGNED_PATHS = new Set(["/auth/login", "/auth/register"]);

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 30000,
  withCredentials: true,
  headers: {
    "Content-Type": "application/json",
  },
});

// Request interceptor
apiClient.interceptors.request.use(
  async (config) => {
    config.headers = config.headers || {};
    const secret = sessionStorage.getItem(SESSION_SIGNATURE_TOKEN_KEY) || null;
    const requestPath = config.url || "";
    if (secret && !UNSIGNED_PATHS.has(requestPath)) {
      const timestamp = generateTimestamp();
      const nonce = generateNonce();
      config.headers["X-Request-Timestamp"] = timestamp;
      config.headers["X-Request-Nonce"] = nonce;

      const body = config.data ? JSON.stringify(config.data) : "";
      const signature = await computeSignature(body, timestamp, nonce, secret);
      if (signature) {
        config.headers["X-Request-Signature"] = signature;
      }
    }

    return config;
  },
  (error) => Promise.reject(error),
);

// Response interceptor
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    // Network error — no response object; let offline handling deal with it
    if (!error.response) {
      return Promise.reject(error);
    }

    const status = error.response.status;

    // 401 Unauthorized — clear auth state and redirect to login
    if (status === 401) {
      sessionStorage.removeItem(SESSION_SIGNATURE_TOKEN_KEY);
      sessionStorage.removeItem(SESSION_USER_KEY);
      const currentPath = window.location.pathname;
      if (currentPath !== "/login") {
        window.location.href = `/login?redirect=${encodeURIComponent(currentPath)}`;
      }
      return Promise.reject(error);
    }

    // 429 Too Many Requests — extract Retry-After and attach to error
    if (status === 429) {
      const retryAfter = error.response.headers["retry-after"];
      if (retryAfter) {
        error.retryAfter = isNaN(Number(retryAfter))
          ? retryAfter
          : Number(retryAfter);
      }
      return Promise.reject(error);
    }

    return Promise.reject(error);
  },
);

export default apiClient;
