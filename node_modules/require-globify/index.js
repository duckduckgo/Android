var glob = require('glob');
var path = require('path');
var fs = require('fs');

var modes = {
  'expand': require('./modes/expand'),
  'hash': require('./modes/hash'),
  'list': require('./modes/list')
};

module.exports = require('browserify-transform-tools').makeRequireTransform(
  'require-globify', {
    jsFilesOnly: true,
    evaluateArguments: true,
    falafelOptions: {ecmaVersion: 6}
  },
  function(args, opts, done) {
    // args: args passed to require()
    // opts: opts used by browserify for the current file
    // done: browserify callback

    var config, pattern, globOpts, mode, result, sei;

    // only trigger if require was used with exactly 2 params that match our expectations
    if (args.length !== 2 || typeof args[0] !== 'string' || typeof args[1] !== 'object') {
      return done();
    }

    // get the second param to require as our config
    config = args[1];

    var skipExtCompat = typeof config.resolve !== 'undefined';

    // backwards compatibility for glob and hash options, replaced by mode
    if (config.glob) {
      config.mode = "expand";
    } else if (config.hash) {
      config.mode = "hash";
      if (config.hash === "path") {
        config.resolve = ["path"];
      }
    }

    // set default resolve option to ["path-reduce", "strip-ext"]
    config.resolve = config.resolve || ["path-reduce", "strip-ext"];
    if (!Array.isArray(config.resolve)) {
      config.resolve = [config.resolve];
    }

    // backwards compatibility for ext option
    if (!skipExtCompat) {
    if (typeof config.ext === 'undefined' || config.ext === false) {
      if (config.resolve.indexOf('strip-ext') === -1) {
        config.resolve.push('strip-ext');
      }
    } else { // remove ext
      sei = config.resolve.indexOf('strip-ext');
      // this wont work if strip-ext is there multiple times
      // but what's the change of that happening?
      if (sei !== -1) {
        config.resolve.splice(sei, 1);
      }
    }
    }

    // if the config object doesn't match our specs, abort
    if (typeof config.mode === 'undefined') {
      console.warn('doesn\'t match require-globify api');
      return done();
    }

    // find mode
    if (typeof config.mode === 'function') {
      mode = config.mode;
    } else if (modes.hasOwnProperty(config.mode)) {
      mode = modes[config.mode];
    } else {
      console.warn("Unknown mode: " + config.mode);
      return done();
    }


    // take the first param to require as pattern
    pattern = args[0];

    // use any additional options given
    globOpts = config.options || {};

    // if no override; set the cwd for glob to the dirname of the current file
    globOpts.cwd = globOpts.cwd || path.dirname(opts.file);
    // only match files
    globOpts.nodir = true;

    glob(pattern, globOpts, function(err, files) {
      // if there was an error with glob, abort here
      if (err) {
        return done(err);
      }

      try {
        // sort files to ensure consistent order upon multiple runs
        files.sort();

        result = mode(opts.file, files, config);

        done(null, result);
      } catch (err) {
        return done(err);
      }

    });
  }
);
