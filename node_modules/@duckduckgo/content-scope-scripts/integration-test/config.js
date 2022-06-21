export default {
    spec_dir: 'integration-test',
    jsLoader: 'import',
    spec_files: [
        '**/*.js',
        '!pages/**/*.js',
        '!extension/**/*.js'
    ],
    random: false
}
