# deep-freeze

recursively `Object.freeze()` objects

[![build status](https://secure.travis-ci.org/substack/deep-freeze.png)](http://travis-ci.org/substack/deep-freeze)

# example

``` js
var deepFreeze = require('deep-freeze');

deepFreeze(Buffer);
Buffer.x = 5;
console.log(Buffer.x === undefined);

Buffer.prototype.z = 3;
console.log(Buffer.prototype.z === undefined);
```

***

```
$ node example/deep.js
true
true
```

# methods

``` js
var deepFreeze = require('deep-freeze')
```

## deepFreeze(obj)

Call `Object.freeze(obj)` recursively on all unfrozen properties of `obj` that
are functions or objects.

# license

public domain

Based in part on the code snippet from
[the MDN wiki page on Object.freeze()](https://developer.mozilla.org/en-US/docs/JavaScript/Reference/Global_Objects/Object/freeze),
which
[is released to the public domain](https://developer.mozilla.org/en-US/docs/Project:Copyrights).
