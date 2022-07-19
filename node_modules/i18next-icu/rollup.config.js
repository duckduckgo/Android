import babel from '@rollup/plugin-babel';
import { terser } from 'rollup-plugin-terser';
import nodeResolve from '@rollup/plugin-node-resolve';
import { argv } from 'yargs';

const format = argv.format || argv.f || 'iife';
const compress = argv.compact;

const babelOptions = {
  exclude: 'node_modules/**',
  presets: [['@babel/preset-env', { modules: false }]],
  babelrc: false
};

const output = [{
  file: `dist/umd/i18nextICU${compress ? '.min' : ''}.js`,
  format: 'umd',
  name: "i18nextICU",
}, {
  file: `dist/amd/i18nextICU${compress ? '.min' : ''}.js`,
  format: 'amd',
  name: "i18nextICU",
}, {
  file: `dist/iife/i18nextICU${compress ? '.min' : ''}.js`,
  format: 'iife',
  name: "i18nextICU",
}];

export default {
  input: 'src/index.js',
  plugins: [
    babel(babelOptions),
    nodeResolve({ mainField: ['jsnext:main'] })
  ].concat(compress ? terser() : []),
  output
};
