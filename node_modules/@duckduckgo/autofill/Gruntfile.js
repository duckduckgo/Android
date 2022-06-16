const {readFileSync} = require('fs')
const {replaceConstExports} = require('./scripts/zod-file-replacer')

module.exports = function (grunt) {
    'use strict'

    grunt.loadNpmTasks('grunt-exec')
    grunt.loadNpmTasks('grunt-eslint')
    grunt.loadNpmTasks('grunt-browserify')
    grunt.loadNpmTasks('grunt-contrib-watch')
    const through = require('through2')

    const defaultTransforms = [
        [
            'babelify', {
                presets: ['@babel/preset-env'],
                global: true
            }
        ],
        [(file) => {
            return through(function (buf, _enc, next) {
                if (!file.endsWith('styles.js')) {
                    this.push(buf)
                    return next()
                }
                const fileContent = readFileSync('./src/UI/styles/autofill-tooltip-styles.css', 'utf8')
                const matcher = '\'$CSS_STYLES$\''
                const asString = buf.toString().replace(matcher, JSON.stringify(fileContent))
                this.push(asString)
                next()
            })
        }]
    ]

    grunt.initConfig({
        pkg: grunt.file.readJSON('package.json'),
        browserify: {
            dist: {
                options: {
                    transform: [
                        ...defaultTransforms,
                        /**
                         * This will replace const exports in `*.zod.js` with 'null' to prevent
                         * `zod` being used in production bundles
                         */
                        [(file) => {
                            return through(function (buf, _enc, next) {
                                if (!file.endsWith('.zod.js')) {
                                    this.push(buf)
                                    return next()
                                }
                                const newFileContent = replaceConstExports(buf.toString())
                                this.push(newFileContent)
                                next()
                            })
                        }]
                    ]
                },
                files: {
                    'dist/autofill.js': ['src/autofill.js']
                }
            },
            debug: {
                options: {
                    // debug transforms just leaves everything in place
                    transform: defaultTransforms
                },
                files: {
                    'dist/autofill-debug.js': ['src/autofill.js']
                }
            }
        },
        eslint: {
            options: {
                configFile: '.eslintrc'
            },
            target: 'src/**/*.js'
        },
        exec: {
            copyAssets: 'npm run copy-assets',
            schemaCompile: 'npm run schema:generate'
        },
        /**
         * Run predefined tasks whenever watched files are added,
         * modified or deleted.
         */
        watch: {
            scripts: {
                files: ['src/**/*.{json,js}', 'packages/password/**/*.{json,js}', 'packages/device-api/**/*.{json,js}'],
                tasks: ['exec:schemaCompile', 'browserify:dist', 'browserify:debug', 'exec:copyAssets']
            },
            html: {
                files: ['src/**/*.html'],
                tasks: ['exec:copyAssets']
            },
            styles: {
                files: ['src/**/*.css', 'src/UI/styles/*'],
                tasks: ['exec:copyAssets']
            }
        }
    })

    grunt.registerTask('default', [
        'exec:schemaCompile',
        'browserify:dist',
        'browserify:debug',
        'exec:copyAssets'
    ])
    grunt.registerTask('dev', ['default', 'watch'])
}
