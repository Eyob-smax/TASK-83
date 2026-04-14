import { describe, it, expect } from "vitest";
import router from "../../src/router/index.js";

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
