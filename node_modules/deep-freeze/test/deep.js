var test = require('tap').test;
var deepFreeze = require('../');

test('deep freeze', function (t) {
    t.plan(2);
    
    deepFreeze(Buffer);
    Buffer.x = 5;
    t.equal(Buffer.x, undefined);
    
    Buffer.prototype.z = 3;
    t.equal(Buffer.prototype.z, undefined);
});
