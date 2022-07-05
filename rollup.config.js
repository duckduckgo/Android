import json from '@rollup/plugin-json'
import copy from 'rollup-plugin-copy'
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
            copy({
                targets: [
                  { src: 'node_modules/@duckduckgo/autoconsent/rules/rules.json', dest: 'autoconsent/autoconsent-impl/libs' },
                ]
            })
        ]
    }
]