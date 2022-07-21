import json from '@rollup/plugin-json';
import typescript from '@rollup/plugin-typescript';
import { terser } from "rollup-plugin-terser";
import pkg from './package.json';

export default [{
  input: './playwright/content.ts',
  output: [{
    file: 'dist/autoconsent.playwright.js',
    format: 'iife'
  }],
  plugins: [
    json(),
    typescript(),
    terser(),
  ]
}, {
  input: './playwright/standalone.ts',
  output: [{
    file: 'dist/autoconsent.standalone.js',
    format: 'iife'
  }],
  plugins: [
    json(),
    typescript(),
    terser(),
  ]
}, {
  input: './lib/web.ts',
  output: [{
    file: pkg.module,
    format: 'es',
    globals: ['browser'],
  }, {
    file: pkg.main,
    format: 'cjs',
  }],
  plugins: [
    typescript(),
    terser(),
  ],
}, {
  input: './addon/background.ts',
  output: [{
    file: './addon/background.bundle.js',
    format: 'iife',
  }],
  plugins: [
    typescript(),
    terser(),
  ]
}, {
  input: './addon/content.ts',
  output: [{
    file: './addon/content.bundle.js',
    format: 'iife',
  }],
  plugins: [
    typescript(),
    terser(),
  ],
}];