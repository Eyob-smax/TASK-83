import { describe, it, expect, beforeEach } from "vitest";
import { setActivePinia, createPinia } from "pinia";
import router from "../../src/router/index.js";
import { useAuthStore } from "../../src/stores/auth.js";

describe("router guard metadata", () => {
  it("login route is explicitly public", () => {
    const login = router.getRoutes().find((route) => route.path === "/login");
    expect(login).toBeTruthy();
    expect(login.meta.requiresAuth).toBe(false);
  });

  it("check-in route restricts access to staff/admin roles", () => {
    const checkIn = router
      .getRoutes()
      .find((route) => route.path === "/checkin");
    expect(checkIn).toBeTruthy();
    expect(checkIn.meta.requiresAuth).toBe(true);
    expect(checkIn.meta.roles).toEqual(["EVENT_STAFF", "SYSTEM_ADMIN"]);
  });

  it("finance routes require finance-manager or system-admin", () => {
    const finance = router
      .getRoutes()
      .find((route) => route.path === "/finance");
    expect(finance).toBeTruthy();
    expect(finance.meta.roles).toEqual(["FINANCE_MANAGER", "SYSTEM_ADMIN"]);
  });

  it("admin routes require system-admin only", () => {
    const adminAudit = router
      .getRoutes()
      .find((route) => route.path === "/admin/audit");
    const adminUsers = router
      .getRoutes()
      .find((route) => route.path === "/admin/users");

    expect(adminAudit).toBeTruthy();
    expect(adminUsers).toBeTruthy();
    expect(adminAudit.meta.roles).toEqual(["SYSTEM_ADMIN"]);
    expect(adminUsers.meta.roles).toEqual(["SYSTEM_ADMIN"]);
  });

  it("export route includes all allowed export roles", () => {
    const exports = router
      .getRoutes()
      .find((route) => route.path === "/exports");
    expect(exports).toBeTruthy();
    expect(exports.meta.roles).toEqual([
      "EVENT_STAFF",
      "FINANCE_MANAGER",
      "SYSTEM_ADMIN",
    ]);
  });
});

describe("router navigation guard behavior", () => {
  beforeEach(() => {
    sessionStorage.clear();
    setActivePinia(createPinia());
  });

  it("redirects unauthenticated users to /login", async () => {
    await router.push("/finance");
    await router.isReady();
    // After guard runs, current route must be /login
    expect(router.currentRoute.value.path).toBe("/login");
    expect(router.currentRoute.value.query.redirect).toBe("/finance");
  });

  it("allows authenticated user with matching role", async () => {
    sessionStorage.setItem(
      "eventops_user",
      JSON.stringify({ id: "u1", roleType: "FINANCE_MANAGER" })
    );
    const auth = useAuthStore();
    auth.initFromSession();
    await router.push("/finance");
    await router.isReady();
    expect(router.currentRoute.value.path).toBe("/finance");
  });

  it("redirects authenticated user without role to / with forbidden=true", async () => {
    sessionStorage.setItem(
      "eventops_user",
      JSON.stringify({ id: "u1", roleType: "ATTENDEE" })
    );
    const auth = useAuthStore();
    auth.initFromSession();
    await router.push("/admin/audit");
    await router.isReady();
    expect(router.currentRoute.value.path).toBe("/");
    expect(router.currentRoute.value.query.forbidden).toBe("true");
  });

  it("allows unauthenticated access to /login (requiresAuth: false)", async () => {
    await router.push("/login");
    await router.isReady();
    expect(router.currentRoute.value.path).toBe("/login");
  });
});
