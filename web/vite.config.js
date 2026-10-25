import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.dirname(fileURLToPath(import.meta.url));
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, root, '');
  const webPort = Number(env.WEB_PORT || 5173);
  if (!Number.isInteger(webPort) || webPort < 1 || webPort > 65535) throw new Error('WEB_PORT must be an integer from 1 to 65535.');
  const apiTarget = env.BCV_API_TARGET || 'http://127.0.0.1:5081';
  const targetUrl = new URL(apiTarget);
  if (!['http:', 'https:'].includes(targetUrl.protocol)) throw new Error('BCV_API_TARGET must be an HTTP/HTTPS URL.');
  // Only the public Maptiles key is exposed. SMTP, PostgreSQL and R2 secrets stay on the server.
  const devFile = path.resolve(root, '../android/dev.properties');
  const mapKey = fs.existsSync(devFile) ? fs.readFileSync(devFile, 'utf8').match(/^bcvGoongMaptilesKey=(.*)$/m)?.[1]?.trim() : '';
  return {
    plugins: [react()],
    define: { __GOONG_MAP_KEY__: JSON.stringify(env.VITE_GOONG_MAPTILES_KEY || mapKey || '') },
    server: { host: env.WEB_HOST || '127.0.0.1', port: webPort, strictPort: true, proxy: { '/api': { target: apiTarget, changeOrigin: true }, '/health': { target: apiTarget } } },
    build: { chunkSizeWarningLimit: 1600 },
  };
});
