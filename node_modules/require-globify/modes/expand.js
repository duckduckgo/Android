module.exports = function(base, files, config) {
  if (files.length === 0) {
    return '';
  }
  return files.reduce(
    function(acc, file, idx, arr) {
      return (acc ? acc + ";" : "") + "require('" + file + "')";
    }, false);
};
