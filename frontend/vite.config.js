import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const configuredPort = Number(env.VITE_DEV_SERVER_PORT ?? 5173);
  const port = Number.isFinite(configuredPort) ? configuredPort : 5173;

  return {
    plugins: [react()],
    server: {
      port
    }
  };
});
