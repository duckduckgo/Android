/**
 * @fileoverview Rule to ensure that there exist no index files
 * @author Stefan Lau
 */

//------------------------------------------------------------------------------
// Rule Definition
//------------------------------------------------------------------------------

var path = require('path'),
    parseFilename = require('../common/parseFilename'),
    isIgnoredFilename = require('../common/isIgnoredFilename'),
    isIndexFile = require('../common/isIndexFile');

module.exports = function(context) {
    return {
        "Program": function(node) {
            var filename = context.getFilename(),
                absoluteFilename = path.resolve(filename),
                parsed = parseFilename(absoluteFilename),
                shouldIgnore = isIgnoredFilename(filename),
                isIndex = isIndexFile(parsed);


            if (shouldIgnore) return;
            if (isIndex) {
                context.report(node, "'index.js' files are not allowed.");
            }
        }
    };

};
