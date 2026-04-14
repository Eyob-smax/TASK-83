import { defineStore } from "pinia";
import { ref, computed } from "vue";
import {
  login as apiLogin,
  logout as apiLogout,
  getCurrentUser,
  refreshSession as apiRefreshSession,
} from "@/api/auth";
import { unwrapResponse } from "@/api/types";

const SESSION_USER_KEY = "eventops_user";
const SESSION_TOKEN_KEY = "eventops_token";
const SIGNATURE_TOKEN_HEADER = "x-session-signature-token";

export const useAuthStore = defineStore("auth", () => {
  const user = ref(null);
  const token = ref(null);

  const isAuthenticated = computed(() => user.value !== null);

  const userRoles = computed(() => {
    if (!user.value || !user.value.roleType) {
      return [];
    }
    return [user.value.roleType];
  });

  function hasRole(role) {
    return userRoles.value.includes(role);
  }

  function hasAnyRole(roles) {
    return roles.some((role) => userRoles.value.includes(role));
  }

  async function login(credentials) {
    try {
      const response = await apiLogin(credentials);
      const data = unwrapResponse(response);
      const signatureToken =
        response?.headers?.[SIGNATURE_TOKEN_HEADER] || null;
      user.value = data.user ?? data;
      token.value = signatureToken;
      sessionStorage.setItem(SESSION_USER_KEY, JSON.stringify(user.value));
      if (token.value) {
        sessionStorage.setItem(SESSION_TOKEN_KEY, token.value);
      }
      return data;
    } catch (error) {
      user.value = null;
      token.value = null;
      throw error;
    }
  }

  async function logout() {
    try {
      await apiLogout();
    } catch {
      // Swallow logout API errors — we clear local state regardless
    } finally {
      user.value = null;
      token.value = null;
      sessionStorage.removeItem(SESSION_USER_KEY);
      sessionStorage.removeItem(SESSION_TOKEN_KEY);
    }
  }

  async function fetchCurrentUser() {
    try {
      const response = await getCurrentUser();
      const data = unwrapResponse(response);
      user.value = data;
      sessionStorage.setItem(SESSION_USER_KEY, JSON.stringify(data));
      return data;
    } catch {
      user.value = null;
      token.value = null;
      sessionStorage.removeItem(SESSION_USER_KEY);
      sessionStorage.removeItem(SESSION_TOKEN_KEY);
      return null;
    }
  }

  async function refreshSession() {
    try {
      const response = await apiRefreshSession();
      const data = unwrapResponse(response);
      const signatureToken =
        response?.headers?.[SIGNATURE_TOKEN_HEADER] || null;
      user.value = data;
      token.value = signatureToken ?? token.value;
      sessionStorage.setItem(SESSION_USER_KEY, JSON.stringify(data));
      if (token.value) {
        sessionStorage.setItem(SESSION_TOKEN_KEY, token.value);
      }
      return data;
    } catch {
      user.value = null;
      token.value = null;
      sessionStorage.removeItem(SESSION_USER_KEY);
      sessionStorage.removeItem(SESSION_TOKEN_KEY);
      return null;
    }
  }

  function initFromSession() {
    const storedUser = sessionStorage.getItem(SESSION_USER_KEY);
    const storedToken = sessionStorage.getItem(SESSION_TOKEN_KEY);
    if (storedUser) {
      try {
        user.value = JSON.parse(storedUser);
      } catch {
        user.value = null;
        sessionStorage.removeItem(SESSION_USER_KEY);
      }
    }
    if (storedToken) {
      token.value = storedToken;
    }
  }

  return {
    user,
    token,
    isAuthenticated,
    userRoles,
    hasRole,
    hasAnyRole,
    login,
    logout,
    fetchCurrentUser,
    refreshSession,
    initFromSession,
  };
});
