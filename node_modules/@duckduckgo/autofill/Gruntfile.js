const {readFileSync} = require('fs')
module.exports = function (grunt) {
    'use strict'

    grunt.loadNpmTasks('grunt-exec')
    grunt.loadNpmTasks('grunt-eslint')
    grunt.loadNpmTasks('grunt-browserify')
    grunt.loadNpmTasks('grunt-contrib-watch')
    const through = require('through2')

    grunt.initConfig({
        pkg: grunt.file.readJSON('package.json'),
        browserify: {
            dist: {
                options: {
                    transform: [
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
                },
                files: {
                    'dist/autofill.js': ['src/autofill.js']
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
            schemaCompile: 'npm run schema:compile'
        },
        /**
         * Run predefined tasks whenever watched files are added,
         * modified or deleted.
         */
        watch: {
            scripts: {
                files: ['src/**/*.js', 'packages/password/**/*.{json,js}'],
                tasks: ['browserify', 'exec:copyAssets']
            },
            schemas: {
                files: ['src/**/*.schema.json'],
                tasks: ['exec:schemaCompile']
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
        'browserify',
        'exec:copyAssets'
    ])
    grunt.registerTask('dev', ['default', 'watch'])
}
