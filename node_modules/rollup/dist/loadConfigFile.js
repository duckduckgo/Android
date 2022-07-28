/*
  @license
	Rollup.js v2.77.2
	Wed, 27 Jul 2022 05:18:41 GMT - commit 22b2f68aa8638ffaac4de69011e10480f5d50546

	https://github.com/rollup/rollup

	Released under the MIT License.
*/
'use strict';

require('path');
require('process');
require('url');
const loadConfigFile_js = require('./shared/loadConfigFile.js');
require('./shared/rollup.js');
require('./shared/mergeOptions.js');
require('tty');
require('perf_hooks');
require('crypto');
require('fs');
require('events');



module.exports = loadConfigFile_js.loadAndParseConfigFile;
//# sourceMappingURL=loadConfigFile.js.map
