/**
 * @fileoverview Rule to ensure that filenames match the exports of the file
 * @author Stefan Lau
 */

//------------------------------------------------------------------------------
// Rule Definition
//------------------------------------------------------------------------------

var path = require('path'),
    camelCase = require('lodash.camelcase'),
    upperFirst = require('lodash.upperfirst'),
    parseFilename = require('../common/parseFilename'),
    isIgnoredFilename = require('../common/isIgnoredFilename'),
    getExportedName = require('../common/getExportedName'),
    isIndexFile = require('../common/isIndexFile'),
    transforms = {
        kebab: require('lodash.kebabcase'),
        snake: require('lodash.snakecase'),
        camel: camelCase,
        pascal: function (name) {
            return upperFirst(camelCase(name));
        }
    },
    transformNames = Object.keys(transforms),
    transformSchema = { "enum": transformNames.concat([ null ]) };

function getStringToCheckAgainstExport(parsed, replacePattern) {
    var dirArray = parsed.dir.split(path.sep);
    var lastDirectory = dirArray[dirArray.length - 1];

    if (isIndexFile(parsed)) {
        return lastDirectory;
    } else {
        return replacePattern ? parsed.name.replace(replacePattern, '') : parsed.name;
    }
}

function getTransformsFromOptions(option) {
    var usedTransforms = (option && option.push) ? option : [ option ];

    return usedTransforms.map(function (name) {
        return transforms[name];
    });
}

function transform(exportedName, transforms) {
    return transforms.map(function (t) {
        return t ? t(exportedName) : exportedName;
    });
}

function anyMatch(expectedExport, transformedNames) {
    return transformedNames.some(function (name) {
        return name === expectedExport;
    });
}

function getWhatToMatchMessage(transforms) {
    if (transforms.length === 1 && !transforms[0]) {
        return "the exported name";
    }
    if (transforms.length > 1) {
        return "any of the exported and transformed names"
    }
    return "the exported and transformed name";
}

module.exports = function(context) {
    return {
        "Program": function (node) {
            var transforms = getTransformsFromOptions(context.options[0]),
                replacePattern = context.options[1] ? new RegExp(context.options[1]) : null,
                filename = context.getFilename(),
                absoluteFilename = path.resolve(filename),
                parsed = parseFilename(absoluteFilename),
                shouldIgnore = isIgnoredFilename(filename),
                exportedName = getExportedName(node, context.options),
                isExporting = Boolean(exportedName),
                expectedExport = getStringToCheckAgainstExport(parsed, replacePattern),
                transformedNames = transform(exportedName, transforms),
                everythingIsIndex = exportedName === 'index' && parsed.name === 'index',
                matchesExported = anyMatch(expectedExport, transformedNames) || everythingIsIndex,
                reportIf = function (condition, messageForNormalFile, messageForIndexFile) {
                    var message = (!messageForIndexFile || !isIndexFile(parsed)) ? messageForNormalFile : messageForIndexFile;

                    if (condition) {
                        context.report(node, message, {
                            name: parsed.base,
                            expectedExport: expectedExport,
                            exportName: transformedNames.join("', '"),
                            extension: parsed.ext,
                            whatToMatch: getWhatToMatchMessage(transforms)
                        });
                    }
                };

            if (shouldIgnore) return;

            reportIf(
                isExporting && !matchesExported,
                "Filename '{{expectedExport}}' must match {{whatToMatch}} '{{exportName}}'.",
                "The directory '{{expectedExport}}' must be named '{{exportName}}', after the exported value of its index file."
            );
        }
    }
};

module.exports.schema = [
    {

        oneOf: [
            transformSchema,
            { type: "array", items: transformSchema, minItems: 1 }
        ]
    },
    {
        type: [ "string", "null" ]
    },
    {
        type: [ "boolean", "null" ]
    }
];
