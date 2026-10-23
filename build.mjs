// Bundles src/ -> www/app.js (the web assets Capacitor packages into the APK).
import { build } from 'esbuild';

await build({
  entryPoints: ['src/main.js'],
  bundle: true,
  minify: true,
  format: 'iife',
  target: 'es2020',
  outfile: 'www/app.js',
  logLevel: 'info',
});
