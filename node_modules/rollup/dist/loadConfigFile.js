/*
  @license
	Rollup.js v2.75.7
	Mon, 20 Jun 2022 07:24:02 GMT - commit 057171c2d3bc2092b7f543fc05ead01f12595f12

	https://github.com/rollup/rollup

	Released under the MIT License.
*/
'use strict';

const loadConfigFile = require('./shared/loadConfigFile.js');
require('path');
require('process');
require('url');
require('./shared/rollup.js');
require('perf_hooks');
require('crypto');
require('fs');
require('events');
require('tty');
require('./shared/mergeOptions.js');



module.exports = loadConfigFile.loadAndParseConfigFile;
//# sourceMappingURL=loadConfigFile.js.map
