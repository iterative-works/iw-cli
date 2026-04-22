// PURPOSE: Vite build configuration for the iw-dashboard frontend
// PURPOSE: Bundles Web Awesome Pro components, Tailwind CSS, and htmx into dist/assets/

import { defineConfig } from "vite";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
  plugins: [tailwindcss()],
  build: {
    outDir: "dist",
    assetsDir: "assets",
    emptyOutDir: true,
    rollupOptions: {
      input: "src/main.js",
      output: {
        entryFileNames: "assets/main.js",
        assetFileNames: "assets/[name][extname]",
      },
    },
  },
  server: {
    cors: true,
  },
});
