/**
 * Unit tests for the AppHeader component.
 *
 * Verifies that the component mounts and renders without error.
 */
import { describe, it, expect } from "vitest";
import { mount } from "@vue/test-utils";
import { createPinia } from "pinia";
import { createMemoryHistory, createRouter } from "vue-router";
import AppHeader from "../../src/components/common/AppHeader.vue";

async function mountHeader() {
  const pinia = createPinia();
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/", component: { template: "<div />" } },
      { path: "/notifications", component: { template: "<div />" } },
      { path: "/login", component: { template: "<div />" } },
    ],
  });

  await router.push("/");
  await router.isReady();

  return mount(AppHeader, {
    global: {
      plugins: [pinia, router],
    },
  });
}

describe("AppHeader", () => {
  it("renders without error", async () => {
    const wrapper = await mountHeader();
    expect(wrapper.exists()).toBe(true);
  });

  it("contains a header element", async () => {
    const wrapper = await mountHeader();
    expect(wrapper.find("header").exists()).toBe(true);
  });
});
