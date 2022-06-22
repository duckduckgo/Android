import { readFile } from 'fs/promises';
import * as rollup from 'rollup';
import commonjs from '@rollup/plugin-commonjs';
import replace from '@rollup/plugin-replace';
import resolve from '@rollup/plugin-node-resolve';
import dynamicImportVariables from 'rollup-plugin-dynamic-import-variables';

const contentScopePath = 'src/content-scope-features.js';
const contentScopeName = 'contentScopeFeatures';

async function rollupScript(scriptPath, name) {
    let mozProxies = false;
    // The code is using a global, that we define here which means once tree shaken we get a browser specific output.
    if (process.argv[2] == "firefox") {
        mozProxies = true;
    }
    const inputOptions = {
        input: scriptPath,
        plugins: [
            resolve(),
            dynamicImportVariables({}),
            commonjs(),
            replace({
                preventAssignment: true,
                values: {
                    mozProxies
                }
            })
        ]
    };
    const outputOptions = {
        dir: 'build',
        format: 'iife',
        inlineDynamicImports: true,
        name: name,
        // This if for seedrandom causing build issues
        globals: { crypto: 'undefined' }
    };

    const bundle = await rollup.rollup(inputOptions);
    const generated = await bundle.generate(outputOptions);
    return generated.output[0].code;
}

async function init() {
    if (process.argv.length != 3) {
        throw new Error("Specify the build type as an argument to this script.");
    }
    if (process.argv[2] == "firefox") {
        initOther('inject/mozilla.js', process.argv[2]);
    } else if (process.argv[2] == "apple") {
        initOther('inject/apple.js', process.argv[2]);
    } else if (process.argv[2] == "integration") {
        initOther('inject/integration.js', process.argv[2]);
    } else {
        initChrome();
    }
}

async function initOther(injectScriptPath, platformName) {
    const replaceString = "/* global contentScopeFeatures */";
    const injectScript = await rollupScript(injectScriptPath, `inject${platformName}`);
    const contentScope = await rollupScript(contentScopePath, contentScopeName);
    const outputScript = injectScript.toString().replace(replaceString, contentScope.toString());
    console.log(outputScript);
}

async function initChrome() {
    const replaceString = "/* global contentScopeFeatures */";
    const injectScriptPath = "inject/chrome.js";
    const injectScript = await readFile(injectScriptPath);
    const contentScope = await rollupScript(contentScopePath, contentScopeName);
    // Encode in URI format to prevent breakage (we could choose to just escape ` instead)
    const encodedString = encodeURI(contentScope.toString());
    const outputScript = injectScript.toString().replace(replaceString, '${decodeURI("' + encodedString + '")}');
    console.log(outputScript);
}

init();
