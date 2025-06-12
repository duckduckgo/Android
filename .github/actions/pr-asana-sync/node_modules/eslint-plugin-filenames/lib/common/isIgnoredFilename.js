var ignoredFilenames = [ "<text>", "<input>" ];

module.exports = function isIgnoredFilename(filename) {
    return ignoredFilenames.indexOf(filename) !== -1;
};
