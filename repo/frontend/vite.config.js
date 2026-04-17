import { defineConfig, loadEnv } from "vite";
import vue from "@vitejs/plugin-vue";
import { resolve } from "path";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const apiProxyTarget = env.VITE_PROXY_TARGET || "https://localhost:8443";

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        "@": resolve(__dirname, "src"),
      },
    },
    server: {
      port: 5173,
      proxy: {
        "/api": {
          target: apiProxyTarget,
          changeOrigin: true,
          secure: false,
        },
      },
    },
    test: {
      environment: "jsdom",
      globals: true,
      root: ".",
      include: ["unit_tests/**/*.test.js"],
      setupFiles: ["unit_tests/setup.js"],
      coverage: {
        provider: "v8",
        reportsDirectory: "./coverage",
        include: ["src/**/*.{js,vue}"],
        exclude: ["src/main.js"],
        reporter: ["text", "html", "lcov"],
        thresholds: {
          lines: 90,
          branches: 75,
          functions: 75,
          statements: 90,
        },
      },
    },
  };
});
