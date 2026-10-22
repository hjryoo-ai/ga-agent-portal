/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react';
import { mkdirSync, writeFileSync } from 'node:fs';
import { relative, resolve } from 'node:path';
import { defineConfig, type Plugin } from 'vite';

const web = import.meta.dirname;

/**
 * 청크마다 묶인 모듈 목록을 build/web/bundle-modules.json으로 남긴다(배포 산출물 밖). scripts/scan-dist.mjs가 이것으로
 * ① 시험 원천이 번들에 없음 ② 금액 연산 정규식을 볼 앱 청크(src 모듈이 든 청크)를 가린다(계획 ③·④-c).
 */
function bundleModules(): Plugin {
  return {
    name: 'portal-bundle-modules',
    apply: 'build',
    generateBundle(_options, bundle) {
      const chunks: Record<string, { entry: boolean; imports: string[]; dynamicImports: string[]; modules: string[] }> = {};
      for (const [fileName, out] of Object.entries(bundle)) {
        if (out.type === 'chunk') {
          chunks[fileName] = {
            entry: out.isEntry,
            imports: [...out.imports],
            dynamicImports: [...out.dynamicImports],
            modules: out.moduleIds.map((id) => relative(web, id.replace(/\?.*$/, '').replace(/^\0/, ''))).sort(),
          };
        }
      }
      mkdirSync(resolve(web, 'build/web'), { recursive: true });
      writeFileSync(resolve(web, 'build/web/bundle-modules.json'), JSON.stringify(chunks, null, 2) + '\n');
    },
  };
}

export default defineConfig({
  root: resolve(web, 'src'),
  base: '/',
  publicDir: false,
  plugins: [react(), bundleModules()],
  build: {
    outDir: resolve(web, 'build/web/dist'),
    emptyOutDir: true,
    assetsDir: 'assets',
    // 자원을 data: URI로 넣지 않는다(CSP img-src·font-src 'self').
    assetsInlineLimit: 0,
    // modulepreload 폴리필은 인라인 스크립트가 될 수 있어 끈다(CSP script-src 'self').
    modulePreload: { polyfill: false },
    sourcemap: false,
    manifest: true,
    rolldownOptions: {
      output: {
        // 라이브러리(node_modules)와 앱 코드를 다른 청크로 — 금액 연산 스캔은 앱 청크만 본다(계획 ④-c).
        codeSplitting: { groups: [{ name: 'vendor', test: /[\\/]node_modules[\\/]/ }] },
      },
    },
  },
  test: {
    root: web,
    include: ['src/**/*.test.{ts,tsx}'],
    environment: 'jsdom',
    reporters: ['default', 'junit'],
    outputFile: { junit: resolve(web, 'build/reports/vitest/junit.xml') },
  },
});
