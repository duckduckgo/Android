var path = require('path');

module.exports = function(base, files, config) {
  var filenames = Object.keys(files);
  // contains map of stripped filenames
  var conflicts = {};
  for (var i=0, l=filenames.length; i<l; i++) {
    (function(file, key) {
      var newKey = key.substr(0, key.length - path.extname(key).length);
      // if already file with same stripping
      if (conflicts.hasOwnProperty(newKey)) {
        // check if first conflict
        if (conflicts[newKey] !== false) {
          // revert previous file stripping
          files[conflicts[newKey][0]] = conflicts[newKey][1];
          conflicts[newKey] = false;
        }
      } else {
        // strip key
        files[file] = newKey;
        // remember for possible later conflicts
        conflicts[newKey] = [file, key];
      }
    })(filenames[i], files[filenames[i]]);
  }
  return files;
}
