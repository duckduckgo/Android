import json from '@rollup/plugin-json'
import { terser } from "rollup-plugin-terser";
import { nodeResolve } from '@rollup/plugin-node-resolve'

export default [
    {
        input: 'autoconsent/autoconsent-impl/libs/userscript.js',
        output: [
            {
                file: 'autoconsent/autoconsent-impl/libs/autoconsent-bundle.js',
                format: 'iife'
            }
        ],
        plugins: [
            nodeResolve(),
            json(),
            terser(),
        ]
    },
    {
        input: 'autoconsent/autoconsent-impl/libs/userscript-exp.js',
        output: [
            {
                file: 'autoconsent/autoconsent-impl/libs/autoconsent-bundle-exp.js',
                format: 'iife'
            }
        ],
        plugins: [
            nodeResolve(),
            json(),
            terser(),
        ]
    }

]
