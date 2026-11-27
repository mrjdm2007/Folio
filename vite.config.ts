import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
export default defineConfig({
  base: "/Folio/",
  plugins: [react()],
  server: { host: "0.0.0.0" },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes("node_modules")) return;
          if (id.includes("/firebase/")) return "firebase";
          if (id.includes("/@tiptap/")) return "editor";
          if (id.includes("/pdfjs-dist/")) return "pdf";
          if (id.includes("/tesseract.js/")) return "ocr";
          if (id.includes("/katex/")) return "math";
          if (id.includes("/mammoth/") || id.includes("/jszip/") || id.includes("/read-excel-file/")) return "documents";
          if (id.includes("/react/") || id.includes("/react-dom/") || id.includes("/scheduler/")) return "vendor";
        }
      }
    }
  }
});
