var acorn = require('acorn');
var isArray = require('isarray');
var util = require('util');

module.exports = function (src, opts, fn) {
    if (typeof opts === 'function') {
        fn = opts;
        opts = {};
    }
    if (src && typeof src === 'object' && src.constructor.name === 'Buffer') {
        src = src.toString();
    }
    else if (src && typeof src === 'object') {
        opts = src;
        src = opts.source;
        delete opts.source;
    }
    src = src === undefined ? opts.source : src;
    if (typeof src !== 'string') src = String(src);
    var parser = opts.parser || acorn;
    var ast = parser.parse(src, opts);
    
    var result = {
        chunks : src.split(''),
        toString : function () { return result.chunks.join('') },
        inspect : function () { return result.toString() }
    };
    if (util.inspect.custom) {
        result[util.inspect.custom] = result.toString;
    }
    var index = 0;
    
    (function walk (node, parent) {
        insertHelpers(node, parent, result.chunks);
        
        for (var key in node) {
            if (key === 'parent' || !Object.prototype.hasOwnProperty.call(node, key)) {
                continue;
            }
            
            var child = node[key];
            if (isArray(child)) {
                for (var i = 0; i < child.length; i += 1) {
                    if (child[i] && typeof child[i].type === 'string') {
                        walk(child[i], node);
                    }
                }
            }
            else if (child && typeof child.type === 'string') {
                walk(child, node);
            }
        }
        fn(node);
    })(ast, undefined);
    
    return result;
};
 
function insertHelpers (node, parent, chunks) {
    node.parent = parent;
    
    node.source = function () {
        return chunks.slice(node.start, node.end).join('');
    };
    
    if (node.update && typeof node.update === 'object') {
        var prev = node.update;
        for (var key in prev) {
            if (Object.prototype.hasOwnProperty.call(prev, key)) {
                update[key] = prev[key];
            }
        }
        node.update = update;
    }
    else {
        node.update = update;
    }
    
    function update (s) {
        chunks[node.start] = s;
        for (var i = node.start + 1; i < node.end; i++) {
            chunks[i] = '';
        }
    }
}
