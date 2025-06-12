var path = require('path');

module.exports = function parseFilename(filename) {
    var ext = path.extname(filename);

    return {
        dir: path.dirname(filename),
        base: path.basename(filename),
        ext: ext,
        name: path.basename(filename, ext)
    }
};
