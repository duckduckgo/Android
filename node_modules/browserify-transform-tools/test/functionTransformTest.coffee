transformTools = require '../src/transformTools'
browserify = require 'browserify'
path = require 'path'
assert = require 'assert'
{expect} = require 'chai'

dummyJsFile = path.resolve __dirname, "../testFixtures/testWithConfig/dummy.js"
testDir = path.resolve __dirname, "../testFixtures/testWithConfig"

describe "transformTools function transforms", ->
  cwd = process.cwd()

  beforeEach ->
    process.chdir testDir

  after ->
    process.chdir cwd

  it "should generate a transform for a given function name", (done) ->
    transform = transformTools.makeFunctionTransform "requireTransform", {functionNames: "bar"}, (functionParams, opts, cb) ->
      if functionParams.args[0].value is "foo"
        cb null, functionParams.name + "('bar')"
      else
        cb()

    content = """
            bar('foo');
            qux('baz');
            """
    expectedContent = """
            bar('bar');
            qux('baz');
            """
    transformTools.runTransform transform, dummyJsFile, {content}, (err, result) ->
      return done err if err
      assert.equal result, expectedContent
      done()

  it "should generate a transform for multiple given function names", (done) ->
    transform = transformTools.makeFunctionTransform "requireTransform", {functionNames: ["bar", "qux"]}, (functionParams, opts, cb) ->
      if functionParams.args[0].value is "foo"
        cb null, functionParams.name + "('bar')"
      else if functionParams.args[0].value is "baz"
          cb null, functionParams.name + "('foobar')"
        else
          cb()

    content = """
            bar('foo');
            qux('baz');
            """
    expectedContent = """
            bar('bar');
            qux('foobar');
            """
    transformTools.runTransform transform, dummyJsFile, {content}, (err, result) ->
      return done err if err
      assert.equal result, expectedContent
      done()

  it "should generate a transform for require as fallback when no function name is provided", (done) ->
    transform = transformTools.makeFunctionTransform "functionTransform", (functionParams, opts, cb) ->
      if functionParams.args[0].value is "foo"
        cb null, functionParams.name + "('bar')"
      else if functionParams.args[0].value is "baz"
          cb null, functionParams.name + "('foobar')"
        else
          cb()

    content = """
            require('foo');
            require('baz');
            """
    expectedContent = """
            require('bar');
            require('foobar');
            """
    transformTools.runTransform transform, dummyJsFile, {content}, (err, result) ->
      return done err if err
      assert.equal result, expectedContent
      done()

  it "should handle multiple function args", (done) ->
    transform = transformTools.makeFunctionTransform "functionTransform", (functionParams, opts, cb) ->
      if functionParams.args[0].value is "foo" and functionParams.args[1].value is "bar"
        cb null, functionParams.name + "('bar', 'foo')"
      else
        cb()

    content = """
            require('foo', 'bar');
            require('baz');
            """
    expectedContent = """
            require('bar', 'foo');
            require('baz');
            """
    transformTools.runTransform transform, dummyJsFile, {content}, (err, result) ->
      return done err if err
      assert.equal result, expectedContent
      done()

  it "should pass the correct arg type", (done) ->
    transform = transformTools.makeFunctionTransform "requireTransform", {functionNames: "bar"}, (functionParams, opts, cb) ->
      concat = ""
      for arg in functionParams.args
        concat += "#{arg.type} "
      cb(null, concat)

    content = """
            bar('foo', foo, function foo(){}, {}, []);
            """
    expectedContent = "Literal Identifier FunctionExpression ObjectExpression ArrayExpression ;"
    transformTools.runTransform transform, dummyJsFile, {content}, (err, result) ->
      return done err if err
      assert.equal result, expectedContent
      done()

  it "should allow load options from configuration", (done) ->
    transform = transformTools.makeFunctionTransform "fooify", (functionParams, opts, cb) ->
      if functionParams.args[0].value is "foo"
        cb null, functionParams.name + "('#{opts.config.foo}')"
      else
        cb()

    content = """
            require('foo');
            require('baz');
            """
    expectedContent = """
            require('bar');
            require('baz');
            """
    transformTools.runTransform transform, dummyJsFile, {content}, (err, result) ->
      return done err if err
      assert.equal result, expectedContent
      done()

  it "should allow manual configuration to override existing configuration", (done) ->
    transform = transformTools.makeFunctionTransform "fooify", (functionParams, opts, cb) ->
      if functionParams.args[0].value is "foo"
        cb null, functionParams.name + "('#{opts.config.foo}')"
      else
        cb()
    transform = transform.configure {foo: "qux"}

    content = """
            require('foo');
            require('baz');
            """
    expectedContent = """
            require('qux');
            require('baz');
            """
    transformTools.runTransform transform, dummyJsFile, {content}, (err, result) ->
      return done err if err
      assert.equal result, expectedContent

      transform = transform.setConfig {foo: "zam"}

      content = """
                require('foo');
                require('baz');
                """
      expectedContent = """
                require('zam');
                require('baz');
                """
      transformTools.runTransform transform, dummyJsFile, {content}, (err, result) ->
        return done err if err
        assert.equal result, expectedContent
        done()

  it "should handle simple expressions", (done) ->
    transform = transformTools.makeFunctionTransform "requireTransform", (functionParams, opts, cb) ->
      if functionParams.args[0].value is "foo"
        cb null, functionParams.name + "('bar')"
      else if functionParams.args[0].value is "a/b"
        cb null, functionParams.name + "('qux')"
      else
        cb()

    content = """
            require('fo' + 'o');
            require(path.join('a', 'b'));
            """
    expectedContent = """
            require('bar');
            require('qux');
            """
    transformTools.runTransform transform, dummyJsFile, {content}, (err, result) ->
      return done err if err
      assert.equal result, expectedContent
      done()

  it "should optionally not handle simple expressions", (done) ->
    transform = transformTools.makeFunctionTransform "requireTransform", {evaluateArguments: false}, (functionParams, opts, cb) ->
      if functionParams.args[0].value is "'foo'"
        cb null, functionParams.name + "('bar')"
      else
        cb()

    content = """
            require('foo');
            require(path.join('a', 'b'));
            """
    expectedContent = """
            require('bar');
            require(path.join('a', 'b'));
            """
    transformTools.runTransform transform, dummyJsFile, {content}, (err, result) ->
      return done err if err
      assert.equal result, expectedContent
      done()

  it "should not gak on expression it doesn't understand", (done) ->
    transform = transformTools.makeFunctionTransform "requireTransform", (functionParams, opts, cb) ->
      if functionParams.args[0].value is "foo"
        cb null, functionParams.name + "('bar')"
      else
        cb()

    content = """
            require(x + y);
            require('foo');
            """
    expectedContent = """
            require(x + y);
            require('bar');
            """
    transformTools.runTransform transform, dummyJsFile, {content}, (err, result) ->
      return done err if err
      assert.equal result, expectedContent
      done()


  it "should return an error when require transform returns an error", (done) ->
    transform = transformTools.makeFunctionTransform "requireTransform", (functionParams, opts, cb) ->
      cb new Error("foo")

    transformTools.runTransform transform, dummyJsFile, {content:"require('boo');"}, (err, result) ->
      expect(err.message).to.match /foo \(while requireTransform was processing .*\/testFixtures\/testWithConfig\/dummy\.js\)/
      done()

  it "should return an error when require transform throws an error", (done) ->
    transform = transformTools.makeFunctionTransform "requireTransform", (functionParams, opts, cb) ->
      throw new Error("foo")

    transformTools.runTransform transform, dummyJsFile, {content:"require('boo');"}, (err, result) ->
      expect(err.message).to.match /foo \(while requireTransform was processing .*\/testFixtures\/testWithConfig\/dummy\.js\)/
      done()

  it "should gracefully handle a syntax error", (done) ->
    transform = transformTools.makeFunctionTransform "requireTransform", (functionParams, opts, cb) ->
      cb()

    content = """
            require('foo');
            require({;
            """
    transformTools.runTransform transform, dummyJsFile, {content}, (err, result) ->
      assert err != null, "Expected an error from runTransform"
      done()
