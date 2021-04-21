(function(){function r(e,n,t){function o(i,f){if(!n[i]){if(!e[i]){var c="function"==typeof require&&require;if(!f&&c)return c(i,!0);if(u)return u(i,!0);var a=new Error("Cannot find module '"+i+"'");throw a.code="MODULE_NOT_FOUND",a}var p=n[i]={exports:{}};e[i][0].call(p.exports,function(r){var n=e[i][1][r];return o(n||r)},p,p.exports,r,e,n,t)}return n[i].exports}for(var u="function"==typeof require&&require,i=0;i<t.length;i++)o(t[i]);return o}return r})()({1:[function(require,module,exports){
/**
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the W3C SOFTWARE AND DOCUMENT NOTICE AND LICENSE.
 *
 *  https://www.w3.org/Consortium/Legal/2015/copyright-software-and-document
 *
 */
(function() {
'use strict';

// Exit early if we're not running in a browser.
if (typeof window !== 'object') {
  return;
}

// Exit early if all IntersectionObserver and IntersectionObserverEntry
// features are natively supported.
if ('IntersectionObserver' in window &&
    'IntersectionObserverEntry' in window &&
    'intersectionRatio' in window.IntersectionObserverEntry.prototype) {

  // Minimal polyfill for Edge 15's lack of `isIntersecting`
  // See: https://github.com/w3c/IntersectionObserver/issues/211
  if (!('isIntersecting' in window.IntersectionObserverEntry.prototype)) {
    Object.defineProperty(window.IntersectionObserverEntry.prototype,
      'isIntersecting', {
      get: function () {
        return this.intersectionRatio > 0;
      }
    });
  }
  return;
}

/**
 * Returns the embedding frame element, if any.
 * @param {!Document} doc
 * @return {!Element}
 */
function getFrameElement(doc) {
  try {
    return doc.defaultView && doc.defaultView.frameElement || null;
  } catch (e) {
    // Ignore the error.
    return null;
  }
}

/**
 * A local reference to the root document.
 */
var document = (function(startDoc) {
  var doc = startDoc;
  var frame = getFrameElement(doc);
  while (frame) {
    doc = frame.ownerDocument;
    frame = getFrameElement(doc);
  }
  return doc;
})(window.document);

/**
 * An IntersectionObserver registry. This registry exists to hold a strong
 * reference to IntersectionObserver instances currently observing a target
 * element. Without this registry, instances without another reference may be
 * garbage collected.
 */
var registry = [];

/**
 * The signal updater for cross-origin intersection. When not null, it means
 * that the polyfill is configured to work in a cross-origin mode.
 * @type {function(DOMRect|ClientRect, DOMRect|ClientRect)}
 */
var crossOriginUpdater = null;

/**
 * The current cross-origin intersection. Only used in the cross-origin mode.
 * @type {DOMRect|ClientRect}
 */
var crossOriginRect = null;


/**
 * Creates the global IntersectionObserverEntry constructor.
 * https://w3c.github.io/IntersectionObserver/#intersection-observer-entry
 * @param {Object} entry A dictionary of instance properties.
 * @constructor
 */
function IntersectionObserverEntry(entry) {
  this.time = entry.time;
  this.target = entry.target;
  this.rootBounds = ensureDOMRect(entry.rootBounds);
  this.boundingClientRect = ensureDOMRect(entry.boundingClientRect);
  this.intersectionRect = ensureDOMRect(entry.intersectionRect || getEmptyRect());
  this.isIntersecting = !!entry.intersectionRect;

  // Calculates the intersection ratio.
  var targetRect = this.boundingClientRect;
  var targetArea = targetRect.width * targetRect.height;
  var intersectionRect = this.intersectionRect;
  var intersectionArea = intersectionRect.width * intersectionRect.height;

  // Sets intersection ratio.
  if (targetArea) {
    // Round the intersection ratio to avoid floating point math issues:
    // https://github.com/w3c/IntersectionObserver/issues/324
    this.intersectionRatio = Number((intersectionArea / targetArea).toFixed(4));
  } else {
    // If area is zero and is intersecting, sets to 1, otherwise to 0
    this.intersectionRatio = this.isIntersecting ? 1 : 0;
  }
}


/**
 * Creates the global IntersectionObserver constructor.
 * https://w3c.github.io/IntersectionObserver/#intersection-observer-interface
 * @param {Function} callback The function to be invoked after intersection
 *     changes have queued. The function is not invoked if the queue has
 *     been emptied by calling the `takeRecords` method.
 * @param {Object=} opt_options Optional configuration options.
 * @constructor
 */
function IntersectionObserver(callback, opt_options) {

  var options = opt_options || {};

  if (typeof callback != 'function') {
    throw new Error('callback must be a function');
  }

  if (options.root && options.root.nodeType != 1) {
    throw new Error('root must be an Element');
  }

  // Binds and throttles `this._checkForIntersections`.
  this._checkForIntersections = throttle(
      this._checkForIntersections.bind(this), this.THROTTLE_TIMEOUT);

  // Private properties.
  this._callback = callback;
  this._observationTargets = [];
  this._queuedEntries = [];
  this._rootMarginValues = this._parseRootMargin(options.rootMargin);

  // Public properties.
  this.thresholds = this._initThresholds(options.threshold);
  this.root = options.root || null;
  this.rootMargin = this._rootMarginValues.map(function(margin) {
    return margin.value + margin.unit;
  }).join(' ');

  /** @private @const {!Array<!Document>} */
  this._monitoringDocuments = [];
  /** @private @const {!Array<function()>} */
  this._monitoringUnsubscribes = [];
}


/**
 * The minimum interval within which the document will be checked for
 * intersection changes.
 */
IntersectionObserver.prototype.THROTTLE_TIMEOUT = 100;


/**
 * The frequency in which the polyfill polls for intersection changes.
 * this can be updated on a per instance basis and must be set prior to
 * calling `observe` on the first target.
 */
IntersectionObserver.prototype.POLL_INTERVAL = null;

/**
 * Use a mutation observer on the root element
 * to detect intersection changes.
 */
IntersectionObserver.prototype.USE_MUTATION_OBSERVER = true;


/**
 * Sets up the polyfill in the cross-origin mode. The result is the
 * updater function that accepts two arguments: `boundingClientRect` and
 * `intersectionRect` - just as these fields would be available to the
 * parent via `IntersectionObserverEntry`. This function should be called
 * each time the iframe receives intersection information from the parent
 * window, e.g. via messaging.
 * @return {function(DOMRect|ClientRect, DOMRect|ClientRect)}
 */
IntersectionObserver._setupCrossOriginUpdater = function() {
  if (!crossOriginUpdater) {
    /**
     * @param {DOMRect|ClientRect} boundingClientRect
     * @param {DOMRect|ClientRect} intersectionRect
     */
    crossOriginUpdater = function(boundingClientRect, intersectionRect) {
      if (!boundingClientRect || !intersectionRect) {
        crossOriginRect = getEmptyRect();
      } else {
        crossOriginRect = convertFromParentRect(boundingClientRect, intersectionRect);
      }
      registry.forEach(function(observer) {
        observer._checkForIntersections();
      });
    };
  }
  return crossOriginUpdater;
};


/**
 * Resets the cross-origin mode.
 */
IntersectionObserver._resetCrossOriginUpdater = function() {
  crossOriginUpdater = null;
  crossOriginRect = null;
};


/**
 * Starts observing a target element for intersection changes based on
 * the thresholds values.
 * @param {Element} target The DOM element to observe.
 */
IntersectionObserver.prototype.observe = function(target) {
  var isTargetAlreadyObserved = this._observationTargets.some(function(item) {
    return item.element == target;
  });

  if (isTargetAlreadyObserved) {
    return;
  }

  if (!(target && target.nodeType == 1)) {
    throw new Error('target must be an Element');
  }

  this._registerInstance();
  this._observationTargets.push({element: target, entry: null});
  this._monitorIntersections(target.ownerDocument);
  this._checkForIntersections();
};


/**
 * Stops observing a target element for intersection changes.
 * @param {Element} target The DOM element to observe.
 */
IntersectionObserver.prototype.unobserve = function(target) {
  this._observationTargets =
      this._observationTargets.filter(function(item) {
        return item.element != target;
      });
  this._unmonitorIntersections(target.ownerDocument);
  if (this._observationTargets.length == 0) {
    this._unregisterInstance();
  }
};


/**
 * Stops observing all target elements for intersection changes.
 */
IntersectionObserver.prototype.disconnect = function() {
  this._observationTargets = [];
  this._unmonitorAllIntersections();
  this._unregisterInstance();
};


/**
 * Returns any queue entries that have not yet been reported to the
 * callback and clears the queue. This can be used in conjunction with the
 * callback to obtain the absolute most up-to-date intersection information.
 * @return {Array} The currently queued entries.
 */
IntersectionObserver.prototype.takeRecords = function() {
  var records = this._queuedEntries.slice();
  this._queuedEntries = [];
  return records;
};


/**
 * Accepts the threshold value from the user configuration object and
 * returns a sorted array of unique threshold values. If a value is not
 * between 0 and 1 and error is thrown.
 * @private
 * @param {Array|number=} opt_threshold An optional threshold value or
 *     a list of threshold values, defaulting to [0].
 * @return {Array} A sorted list of unique and valid threshold values.
 */
IntersectionObserver.prototype._initThresholds = function(opt_threshold) {
  var threshold = opt_threshold || [0];
  if (!Array.isArray(threshold)) threshold = [threshold];

  return threshold.sort().filter(function(t, i, a) {
    if (typeof t != 'number' || isNaN(t) || t < 0 || t > 1) {
      throw new Error('threshold must be a number between 0 and 1 inclusively');
    }
    return t !== a[i - 1];
  });
};


/**
 * Accepts the rootMargin value from the user configuration object
 * and returns an array of the four margin values as an object containing
 * the value and unit properties. If any of the values are not properly
 * formatted or use a unit other than px or %, and error is thrown.
 * @private
 * @param {string=} opt_rootMargin An optional rootMargin value,
 *     defaulting to '0px'.
 * @return {Array<Object>} An array of margin objects with the keys
 *     value and unit.
 */
IntersectionObserver.prototype._parseRootMargin = function(opt_rootMargin) {
  var marginString = opt_rootMargin || '0px';
  var margins = marginString.split(/\s+/).map(function(margin) {
    var parts = /^(-?\d*\.?\d+)(px|%)$/.exec(margin);
    if (!parts) {
      throw new Error('rootMargin must be specified in pixels or percent');
    }
    return {value: parseFloat(parts[1]), unit: parts[2]};
  });

  // Handles shorthand.
  margins[1] = margins[1] || margins[0];
  margins[2] = margins[2] || margins[0];
  margins[3] = margins[3] || margins[1];

  return margins;
};


/**
 * Starts polling for intersection changes if the polling is not already
 * happening, and if the page's visibility state is visible.
 * @param {!Document} doc
 * @private
 */
IntersectionObserver.prototype._monitorIntersections = function(doc) {
  var win = doc.defaultView;
  if (!win) {
    // Already destroyed.
    return;
  }
  if (this._monitoringDocuments.indexOf(doc) != -1) {
    // Already monitoring.
    return;
  }

  // Private state for monitoring.
  var callback = this._checkForIntersections;
  var monitoringInterval = null;
  var domObserver = null;

  // If a poll interval is set, use polling instead of listening to
  // resize and scroll events or DOM mutations.
  if (this.POLL_INTERVAL) {
    monitoringInterval = win.setInterval(callback, this.POLL_INTERVAL);
  } else {
    addEvent(win, 'resize', callback, true);
    addEvent(doc, 'scroll', callback, true);
    if (this.USE_MUTATION_OBSERVER && 'MutationObserver' in win) {
      domObserver = new win.MutationObserver(callback);
      domObserver.observe(doc, {
        attributes: true,
        childList: true,
        characterData: true,
        subtree: true
      });
    }
  }

  this._monitoringDocuments.push(doc);
  this._monitoringUnsubscribes.push(function() {
    // Get the window object again. When a friendly iframe is destroyed, it
    // will be null.
    var win = doc.defaultView;

    if (win) {
      if (monitoringInterval) {
        win.clearInterval(monitoringInterval);
      }
      removeEvent(win, 'resize', callback, true);
    }

    removeEvent(doc, 'scroll', callback, true);
    if (domObserver) {
      domObserver.disconnect();
    }
  });

  // Also monitor the parent.
  if (doc != (this.root && this.root.ownerDocument || document)) {
    var frame = getFrameElement(doc);
    if (frame) {
      this._monitorIntersections(frame.ownerDocument);
    }
  }
};


/**
 * Stops polling for intersection changes.
 * @param {!Document} doc
 * @private
 */
IntersectionObserver.prototype._unmonitorIntersections = function(doc) {
  var index = this._monitoringDocuments.indexOf(doc);
  if (index == -1) {
    return;
  }

  var rootDoc = (this.root && this.root.ownerDocument || document);

  // Check if any dependent targets are still remaining.
  var hasDependentTargets =
      this._observationTargets.some(function(item) {
        var itemDoc = item.element.ownerDocument;
        // Target is in this context.
        if (itemDoc == doc) {
          return true;
        }
        // Target is nested in this context.
        while (itemDoc && itemDoc != rootDoc) {
          var frame = getFrameElement(itemDoc);
          itemDoc = frame && frame.ownerDocument;
          if (itemDoc == doc) {
            return true;
          }
        }
        return false;
      });
  if (hasDependentTargets) {
    return;
  }

  // Unsubscribe.
  var unsubscribe = this._monitoringUnsubscribes[index];
  this._monitoringDocuments.splice(index, 1);
  this._monitoringUnsubscribes.splice(index, 1);
  unsubscribe();

  // Also unmonitor the parent.
  if (doc != rootDoc) {
    var frame = getFrameElement(doc);
    if (frame) {
      this._unmonitorIntersections(frame.ownerDocument);
    }
  }
};


/**
 * Stops polling for intersection changes.
 * @param {!Document} doc
 * @private
 */
IntersectionObserver.prototype._unmonitorAllIntersections = function() {
  var unsubscribes = this._monitoringUnsubscribes.slice(0);
  this._monitoringDocuments.length = 0;
  this._monitoringUnsubscribes.length = 0;
  for (var i = 0; i < unsubscribes.length; i++) {
    unsubscribes[i]();
  }
};


/**
 * Scans each observation target for intersection changes and adds them
 * to the internal entries queue. If new entries are found, it
 * schedules the callback to be invoked.
 * @private
 */
IntersectionObserver.prototype._checkForIntersections = function() {
  if (!this.root && crossOriginUpdater && !crossOriginRect) {
    // Cross origin monitoring, but no initial data available yet.
    return;
  }

  var rootIsInDom = this._rootIsInDom();
  var rootRect = rootIsInDom ? this._getRootRect() : getEmptyRect();

  this._observationTargets.forEach(function(item) {
    var target = item.element;
    var targetRect = getBoundingClientRect(target);
    var rootContainsTarget = this._rootContainsTarget(target);
    var oldEntry = item.entry;
    var intersectionRect = rootIsInDom && rootContainsTarget &&
        this._computeTargetAndRootIntersection(target, targetRect, rootRect);

    var newEntry = item.entry = new IntersectionObserverEntry({
      time: now(),
      target: target,
      boundingClientRect: targetRect,
      rootBounds: crossOriginUpdater && !this.root ? null : rootRect,
      intersectionRect: intersectionRect
    });

    if (!oldEntry) {
      this._queuedEntries.push(newEntry);
    } else if (rootIsInDom && rootContainsTarget) {
      // If the new entry intersection ratio has crossed any of the
      // thresholds, add a new entry.
      if (this._hasCrossedThreshold(oldEntry, newEntry)) {
        this._queuedEntries.push(newEntry);
      }
    } else {
      // If the root is not in the DOM or target is not contained within
      // root but the previous entry for this target had an intersection,
      // add a new record indicating removal.
      if (oldEntry && oldEntry.isIntersecting) {
        this._queuedEntries.push(newEntry);
      }
    }
  }, this);

  if (this._queuedEntries.length) {
    this._callback(this.takeRecords(), this);
  }
};


/**
 * Accepts a target and root rect computes the intersection between then
 * following the algorithm in the spec.
 * TODO(philipwalton): at this time clip-path is not considered.
 * https://w3c.github.io/IntersectionObserver/#calculate-intersection-rect-algo
 * @param {Element} target The target DOM element
 * @param {Object} targetRect The bounding rect of the target.
 * @param {Object} rootRect The bounding rect of the root after being
 *     expanded by the rootMargin value.
 * @return {?Object} The final intersection rect object or undefined if no
 *     intersection is found.
 * @private
 */
IntersectionObserver.prototype._computeTargetAndRootIntersection =
    function(target, targetRect, rootRect) {
  // If the element isn't displayed, an intersection can't happen.
  if (window.getComputedStyle(target).display == 'none') return;

  var intersectionRect = targetRect;
  var parent = getParentNode(target);
  var atRoot = false;

  while (!atRoot && parent) {
    var parentRect = null;
    var parentComputedStyle = parent.nodeType == 1 ?
        window.getComputedStyle(parent) : {};

    // If the parent isn't displayed, an intersection can't happen.
    if (parentComputedStyle.display == 'none') return null;

    if (parent == this.root || parent.nodeType == /* DOCUMENT */ 9) {
      atRoot = true;
      if (parent == this.root || parent == document) {
        if (crossOriginUpdater && !this.root) {
          if (!crossOriginRect ||
              crossOriginRect.width == 0 && crossOriginRect.height == 0) {
            // A 0-size cross-origin intersection means no-intersection.
            parent = null;
            parentRect = null;
            intersectionRect = null;
          } else {
            parentRect = crossOriginRect;
          }
        } else {
          parentRect = rootRect;
        }
      } else {
        // Check if there's a frame that can be navigated to.
        var frame = getParentNode(parent);
        var frameRect = frame && getBoundingClientRect(frame);
        var frameIntersect =
            frame &&
            this._computeTargetAndRootIntersection(frame, frameRect, rootRect);
        if (frameRect && frameIntersect) {
          parent = frame;
          parentRect = convertFromParentRect(frameRect, frameIntersect);
        } else {
          parent = null;
          intersectionRect = null;
        }
      }
    } else {
      // If the element has a non-visible overflow, and it's not the <body>
      // or <html> element, update the intersection rect.
      // Note: <body> and <html> cannot be clipped to a rect that's not also
      // the document rect, so no need to compute a new intersection.
      var doc = parent.ownerDocument;
      if (parent != doc.body &&
          parent != doc.documentElement &&
          parentComputedStyle.overflow != 'visible') {
        parentRect = getBoundingClientRect(parent);
      }
    }

    // If either of the above conditionals set a new parentRect,
    // calculate new intersection data.
    if (parentRect) {
      intersectionRect = computeRectIntersection(parentRect, intersectionRect);
    }
    if (!intersectionRect) break;
    parent = parent && getParentNode(parent);
  }
  return intersectionRect;
};


/**
 * Returns the root rect after being expanded by the rootMargin value.
 * @return {ClientRect} The expanded root rect.
 * @private
 */
IntersectionObserver.prototype._getRootRect = function() {
  var rootRect;
  if (this.root) {
    rootRect = getBoundingClientRect(this.root);
  } else {
    // Use <html>/<body> instead of window since scroll bars affect size.
    var html = document.documentElement;
    var body = document.body;
    rootRect = {
      top: 0,
      left: 0,
      right: html.clientWidth || body.clientWidth,
      width: html.clientWidth || body.clientWidth,
      bottom: html.clientHeight || body.clientHeight,
      height: html.clientHeight || body.clientHeight
    };
  }
  return this._expandRectByRootMargin(rootRect);
};


/**
 * Accepts a rect and expands it by the rootMargin value.
 * @param {DOMRect|ClientRect} rect The rect object to expand.
 * @return {ClientRect} The expanded rect.
 * @private
 */
IntersectionObserver.prototype._expandRectByRootMargin = function(rect) {
  var margins = this._rootMarginValues.map(function(margin, i) {
    return margin.unit == 'px' ? margin.value :
        margin.value * (i % 2 ? rect.width : rect.height) / 100;
  });
  var newRect = {
    top: rect.top - margins[0],
    right: rect.right + margins[1],
    bottom: rect.bottom + margins[2],
    left: rect.left - margins[3]
  };
  newRect.width = newRect.right - newRect.left;
  newRect.height = newRect.bottom - newRect.top;

  return newRect;
};


/**
 * Accepts an old and new entry and returns true if at least one of the
 * threshold values has been crossed.
 * @param {?IntersectionObserverEntry} oldEntry The previous entry for a
 *    particular target element or null if no previous entry exists.
 * @param {IntersectionObserverEntry} newEntry The current entry for a
 *    particular target element.
 * @return {boolean} Returns true if a any threshold has been crossed.
 * @private
 */
IntersectionObserver.prototype._hasCrossedThreshold =
    function(oldEntry, newEntry) {

  // To make comparing easier, an entry that has a ratio of 0
  // but does not actually intersect is given a value of -1
  var oldRatio = oldEntry && oldEntry.isIntersecting ?
      oldEntry.intersectionRatio || 0 : -1;
  var newRatio = newEntry.isIntersecting ?
      newEntry.intersectionRatio || 0 : -1;

  // Ignore unchanged ratios
  if (oldRatio === newRatio) return;

  for (var i = 0; i < this.thresholds.length; i++) {
    var threshold = this.thresholds[i];

    // Return true if an entry matches a threshold or if the new ratio
    // and the old ratio are on the opposite sides of a threshold.
    if (threshold == oldRatio || threshold == newRatio ||
        threshold < oldRatio !== threshold < newRatio) {
      return true;
    }
  }
};


/**
 * Returns whether or not the root element is an element and is in the DOM.
 * @return {boolean} True if the root element is an element and is in the DOM.
 * @private
 */
IntersectionObserver.prototype._rootIsInDom = function() {
  return !this.root || containsDeep(document, this.root);
};


/**
 * Returns whether or not the target element is a child of root.
 * @param {Element} target The target element to check.
 * @return {boolean} True if the target element is a child of root.
 * @private
 */
IntersectionObserver.prototype._rootContainsTarget = function(target) {
  return containsDeep(this.root || document, target) &&
    (!this.root || this.root.ownerDocument == target.ownerDocument);
};


/**
 * Adds the instance to the global IntersectionObserver registry if it isn't
 * already present.
 * @private
 */
IntersectionObserver.prototype._registerInstance = function() {
  if (registry.indexOf(this) < 0) {
    registry.push(this);
  }
};


/**
 * Removes the instance from the global IntersectionObserver registry.
 * @private
 */
IntersectionObserver.prototype._unregisterInstance = function() {
  var index = registry.indexOf(this);
  if (index != -1) registry.splice(index, 1);
};


/**
 * Returns the result of the performance.now() method or null in browsers
 * that don't support the API.
 * @return {number} The elapsed time since the page was requested.
 */
function now() {
  return window.performance && performance.now && performance.now();
}


/**
 * Throttles a function and delays its execution, so it's only called at most
 * once within a given time period.
 * @param {Function} fn The function to throttle.
 * @param {number} timeout The amount of time that must pass before the
 *     function can be called again.
 * @return {Function} The throttled function.
 */
function throttle(fn, timeout) {
  var timer = null;
  return function () {
    if (!timer) {
      timer = setTimeout(function() {
        fn();
        timer = null;
      }, timeout);
    }
  };
}


/**
 * Adds an event handler to a DOM node ensuring cross-browser compatibility.
 * @param {Node} node The DOM node to add the event handler to.
 * @param {string} event The event name.
 * @param {Function} fn The event handler to add.
 * @param {boolean} opt_useCapture Optionally adds the even to the capture
 *     phase. Note: this only works in modern browsers.
 */
function addEvent(node, event, fn, opt_useCapture) {
  if (typeof node.addEventListener == 'function') {
    node.addEventListener(event, fn, opt_useCapture || false);
  }
  else if (typeof node.attachEvent == 'function') {
    node.attachEvent('on' + event, fn);
  }
}


/**
 * Removes a previously added event handler from a DOM node.
 * @param {Node} node The DOM node to remove the event handler from.
 * @param {string} event The event name.
 * @param {Function} fn The event handler to remove.
 * @param {boolean} opt_useCapture If the event handler was added with this
 *     flag set to true, it should be set to true here in order to remove it.
 */
function removeEvent(node, event, fn, opt_useCapture) {
  if (typeof node.removeEventListener == 'function') {
    node.removeEventListener(event, fn, opt_useCapture || false);
  }
  else if (typeof node.detatchEvent == 'function') {
    node.detatchEvent('on' + event, fn);
  }
}


/**
 * Returns the intersection between two rect objects.
 * @param {Object} rect1 The first rect.
 * @param {Object} rect2 The second rect.
 * @return {?Object|?ClientRect} The intersection rect or undefined if no
 *     intersection is found.
 */
function computeRectIntersection(rect1, rect2) {
  var top = Math.max(rect1.top, rect2.top);
  var bottom = Math.min(rect1.bottom, rect2.bottom);
  var left = Math.max(rect1.left, rect2.left);
  var right = Math.min(rect1.right, rect2.right);
  var width = right - left;
  var height = bottom - top;

  return (width >= 0 && height >= 0) && {
    top: top,
    bottom: bottom,
    left: left,
    right: right,
    width: width,
    height: height
  } || null;
}


/**
 * Shims the native getBoundingClientRect for compatibility with older IE.
 * @param {Element} el The element whose bounding rect to get.
 * @return {DOMRect|ClientRect} The (possibly shimmed) rect of the element.
 */
function getBoundingClientRect(el) {
  var rect;

  try {
    rect = el.getBoundingClientRect();
  } catch (err) {
    // Ignore Windows 7 IE11 "Unspecified error"
    // https://github.com/w3c/IntersectionObserver/pull/205
  }

  if (!rect) return getEmptyRect();

  // Older IE
  if (!(rect.width && rect.height)) {
    rect = {
      top: rect.top,
      right: rect.right,
      bottom: rect.bottom,
      left: rect.left,
      width: rect.right - rect.left,
      height: rect.bottom - rect.top
    };
  }
  return rect;
}


/**
 * Returns an empty rect object. An empty rect is returned when an element
 * is not in the DOM.
 * @return {ClientRect} The empty rect.
 */
function getEmptyRect() {
  return {
    top: 0,
    bottom: 0,
    left: 0,
    right: 0,
    width: 0,
    height: 0
  };
}


/**
 * Ensure that the result has all of the necessary fields of the DOMRect.
 * Specifically this ensures that `x` and `y` fields are set.
 *
 * @param {?DOMRect|?ClientRect} rect
 * @return {?DOMRect}
 */
function ensureDOMRect(rect) {
  // A `DOMRect` object has `x` and `y` fields.
  if (!rect || 'x' in rect) {
    return rect;
  }
  // A IE's `ClientRect` type does not have `x` and `y`. The same is the case
  // for internally calculated Rect objects. For the purposes of
  // `IntersectionObserver`, it's sufficient to simply mirror `left` and `top`
  // for these fields.
  return {
    top: rect.top,
    y: rect.top,
    bottom: rect.bottom,
    left: rect.left,
    x: rect.left,
    right: rect.right,
    width: rect.width,
    height: rect.height
  };
}


/**
 * Inverts the intersection and bounding rect from the parent (frame) BCR to
 * the local BCR space.
 * @param {DOMRect|ClientRect} parentBoundingRect The parent's bound client rect.
 * @param {DOMRect|ClientRect} parentIntersectionRect The parent's own intersection rect.
 * @return {ClientRect} The local root bounding rect for the parent's children.
 */
function convertFromParentRect(parentBoundingRect, parentIntersectionRect) {
  var top = parentIntersectionRect.top - parentBoundingRect.top;
  var left = parentIntersectionRect.left - parentBoundingRect.left;
  return {
    top: top,
    left: left,
    height: parentIntersectionRect.height,
    width: parentIntersectionRect.width,
    bottom: top + parentIntersectionRect.height,
    right: left + parentIntersectionRect.width
  };
}


/**
 * Checks to see if a parent element contains a child element (including inside
 * shadow DOM).
 * @param {Node} parent The parent element.
 * @param {Node} child The child element.
 * @return {boolean} True if the parent node contains the child node.
 */
function containsDeep(parent, child) {
  var node = child;
  while (node) {
    if (node == parent) return true;

    node = getParentNode(node);
  }
  return false;
}


/**
 * Gets the parent node of an element or its host element if the parent node
 * is a shadow root.
 * @param {Node} node The node whose parent to get.
 * @return {Node|null} The parent node or null if no parent exists.
 */
function getParentNode(node) {
  var parent = node.parentNode;

  if (node.nodeType == /* DOCUMENT */ 9 && node != document) {
    // If this node is a document node, look for the embedding frame.
    return getFrameElement(node);
  }

  if (parent && parent.nodeType == 11 && parent.host) {
    // If the parent is a shadow root, return the host element.
    return parent.host;
  }

  if (parent && parent.assignedSlot) {
    // If the parent is distributed in a <slot>, return the parent of a slot.
    return parent.assignedSlot.parentNode;
  }

  return parent;
}


// Exposes the constructors globally.
window.IntersectionObserver = IntersectionObserver;
window.IntersectionObserverEntry = IntersectionObserverEntry;

}());

},{}],2:[function(require,module,exports){
"use strict";

module.exports = "\n.wrapper *, .wrapper *::before, .wrapper *::after {\n    box-sizing: border-box;\n}\n.wrapper {\n    position: fixed;\n    top: 0;\n    left: 0;\n    padding: 0;\n    font-family: 'DDG_ProximaNova', 'Proxima Nova', -apple-system,\n    BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Oxygen', 'Ubuntu',\n    'Cantarell', 'Fira Sans', 'Droid Sans', 'Helvetica Neue', sans-serif;\n    -webkit-font-smoothing: antialiased;\n    z-index: 2147483647;\n}\n.tooltip {\n    position: absolute;\n    bottom: calc(100% + 15px);\n    right: calc(100% - 60px);\n    width: 350px;\n    max-width: calc(100vw - 25px);\n    padding: 25px;\n    border: 1px solid #D0D0D0;\n    border-radius: 20px;\n    background-color: #FFFFFF;\n    font-size: 14px;\n    color: #333333;\n    line-height: 1.4;\n    box-shadow: 0 10px 20px rgba(0, 0, 0, 0.15);\n    z-index: 2147483647;\n}\n.tooltip::before {\n    content: \"\";\n    width: 0;\n    height: 0;\n    border-left: 10px solid transparent;\n    border-right: 10px solid transparent;\n    display: block;\n    border-top: 12px solid #D0D0D0;\n    position: absolute;\n    right: 34px;\n    bottom: -12px;\n}\n.tooltip::after {\n    content: \"\";\n    width: 0;\n    height: 0;\n    border-left: 10px solid transparent;\n    border-right: 10px solid transparent;\n    display: block;\n    border-top: 12px solid #FFFFFF;\n    position: absolute;\n    right: 34px;\n    bottom: -10px;\n}\n.tooltip__title {\n    margin: -4px 0 4px;\n    font-size: 16px;\n    font-weight: bold;\n    line-height: 1.3;\n}\n.tooltip p {\n    margin: 4px 0 12px;\n}\n.tooltip strong {\n    font-weight: bold;\n}\n.tooltip__alias-container {\n    display: flex;\n    justify-content: center;\n    align-items: center;\n    margin: 10px auto 15px;\n    font-size: 16px;\n}\n.alias {\n    padding-left: 4px;\n}\n.tooltip__button-container {\n    display: flex;\n}\n.tooltip__button {\n    flex: 1;\n    height: 40px;\n    padding: 0 8px;\n    background-color: #332FF3;\n    color: #FFFFFF;\n    font-family: inherit;\n    font-size: 16px;\n    font-weight: bold;\n    border: none;\n    border-radius: 10px;\n}\n.tooltip__button:last-child {\n    margin-left: 12px;\n}\n.tooltip__button--secondary {\n    background-color: #EEEEEE;\n    color: #332FF3;\n}\n";

},{}],3:[function(require,module,exports){
const {daxSvg} = require('./logo-svg')
const { isMacOSApp, getDaxBoundingBox, safeExecute } = require('./autofill-utils')

class DDGAutofill {
    constructor (input, associatedForm, getAlias, refreshAlias) {
        const shadow = document.createElement('ddg-autofill').attachShadow({mode: 'closed'})
        this.host = shadow.host
        this.input = input
        this.associatedForm = associatedForm
        this.animationFrame = null

        const includeStyles = isMacOSApp
            ? `<style>${require('./DDGAutofill-styles.js')}</style>`
            : `<link rel="stylesheet" href="${chrome.runtime.getURL('public/css/email-autofill.css')}">`

        shadow.innerHTML = `
${includeStyles}
<div class="wrapper">
    <div class="tooltip" hidden>
        <h2 class="tooltip__title">Use a Private Duck Address</h2>
        <p>Protect your personal address, block trackers, and forward to your regular inbox. </p>
        <div class="tooltip__alias-container">${daxSvg}<strong class="alias">${this.nextAlias}</strong>@duck.com</div>
        <div class="tooltip__button-container">
            <button class="tooltip__button tooltip__button--secondary js-dismiss">Don’t use</button>
            <button class="tooltip__button tooltip__button--primary js-confirm">Use Address</button>
        </div>
    </div>
</div>`
        this.wrapper = shadow.querySelector('.wrapper')
        this.tooltip = shadow.querySelector('.tooltip')
        this.dismissButton = shadow.querySelector('.js-dismiss')
        this.confirmButton = shadow.querySelector('.js-confirm')
        this.aliasEl = shadow.querySelector('.alias')
        this.stylesheet = shadow.querySelector('link, style')
        // Un-hide once the style is loaded, to avoid flashing unstyled content
        this.stylesheet.addEventListener('load', () =>
            this.tooltip.removeAttribute('hidden'))

        this.updateAliasInTooltip = () => {
            const [alias] = this.nextAlias.split('@')
            this.aliasEl.textContent = alias
        }

        // Get the alias from the extension
        getAlias().then((alias) => {
            if (alias) {
                this.nextAlias = alias
                this.updateAliasInTooltip()
            }
        })

        this.top = 0
        this.left = 0
        this.transformRuleIndex = null
        this.updatePosition = ({left, top}) => {
            // If the stylesheet is not loaded wait for load (Chrome bug)
            if (!shadow.styleSheets.length) return this.stylesheet.addEventListener('load', this.checkPosition)

            this.left = left
            this.top = top

            if (this.transformRuleIndex && shadow.styleSheets[this.transformRuleIndex]) {
                // If we have already set the rule, remove it…
                shadow.styleSheets[0].deleteRule(this.transformRuleIndex)
            } else {
                // …otherwise, set the index as the very last rule
                this.transformRuleIndex = shadow.styleSheets[0].rules.length
            }

            const newRule = `.wrapper {transform: translate(${left}px, ${top}px);}`
            shadow.styleSheets[0].insertRule(newRule, this.transformRuleIndex)
        }

        this.append = () => document.body.appendChild(shadow.host)
        this.append()
        this.lift = () => {
            this.left = null
            this.top = null
            document.body.removeChild(this.host)
        }

        this.remove = () => {
            window.removeEventListener('scroll', this.checkPosition, {passive: true, capture: true})
            this.resObs.disconnect()
            this.mutObs.disconnect()
            this.lift()
        }

        this.checkPosition = () => {
            if (this.animationFrame) {
                window.cancelAnimationFrame(this.animationFrame)
            }

            this.animationFrame = window.requestAnimationFrame(() => {
                const {left, top} = getDaxBoundingBox(this.input)

                if (left !== this.left || top !== this.top) {
                    this.updatePosition({left, top})
                }

                this.animationFrame = null
            })
        }
        this.resObs = new ResizeObserver(entries => entries.forEach(this.checkPosition))
        this.resObs.observe(document.body)
        this.count = 0
        this.ensureIsLastInDOM = () => {
            // If DDG el is not the last in the doc, move them there
            if (document.body.lastElementChild !== this.host) {
                this.lift()

                // Try up to 5 times to avoid infinite loop in case someone is doing the same
                if (this.count < 15) {
                    this.append()
                    this.checkPosition()
                    this.count++
                } else {
                    // Reset count so we can resume normal flow
                    this.count = 0
                    console.info(`DDG autofill bailing out`)
                }
            }
        }
        this.mutObs = new MutationObserver((mutationList) => {
            for (const mutationRecord of mutationList) {
                if (mutationRecord.type === 'childList') {
                    // Only check added nodes
                    mutationRecord.addedNodes.forEach(el => {
                        if (el.nodeName === 'DDG-AUTOFILL') return

                        this.ensureIsLastInDOM()
                    })
                }
            }
            this.checkPosition()
        })
        this.mutObs.observe(document.body, {childList: true, subtree: true, attributes: true})
        window.addEventListener('scroll', this.checkPosition, {passive: true, capture: true})

        this.dismissButton.addEventListener('click', (e) => {
            if (!e.isTrusted) return

            e.stopImmediatePropagation()
            this.associatedForm.dismissTooltip()
        })
        this.confirmButton.addEventListener('click', (e) => {
            if (!e.isTrusted) return
            e.stopImmediatePropagation()

            safeExecute(this.confirmButton, () => {
                this.associatedForm.autofill(this.nextAlias)
                refreshAlias()
            })
        })
    }
}

module.exports = DDGAutofill

},{"./DDGAutofill-styles.js":2,"./autofill-utils":7,"./logo-svg":9}],4:[function(require,module,exports){
"use strict";

function _classCallCheck(instance, Constructor) { if (!(instance instanceof Constructor)) { throw new TypeError("Cannot call a class as a function"); } }

var DDGAutofill = require('./DDGAutofill');

var _require = require('./autofill-utils'),
    isMacOSApp = _require.isMacOSApp,
    notifyWebApp = _require.notifyWebApp,
    isDDGApp = _require.isDDGApp,
    isAndroid = _require.isAndroid,
    isDDGDomain = _require.isDDGDomain,
    sendAndWaitForAnswer = _require.sendAndWaitForAnswer,
    setValue = _require.setValue;

var scanForInputs = require('./scanForInputs.js');

var SIGN_IN_MSG = {
  signMeIn: true
};

var createAttachTooltip = function createAttachTooltip(getAlias, refreshAlias) {
  return function (form, input) {
    if (isDDGApp && !isMacOSApp) {
      form.activeInput = input;
      getAlias().then(function (alias) {
        if (alias) form.autofill(alias);else form.activeInput.focus();
      });
    } else {
      if (form.tooltip) return;
      form.tooltip = new DDGAutofill(input, form, getAlias, refreshAlias);
      form.intObs.observe(input);
      window.addEventListener('mousedown', form.removeTooltip, {
        capture: true
      });
    }
  };
};

var ExtensionInterface = function ExtensionInterface() {
  var _this = this;

  _classCallCheck(this, ExtensionInterface);

  this.getAlias = function () {
    return new Promise(function (resolve) {
      return chrome.runtime.sendMessage({
        getAlias: true
      }, function (_ref) {
        var alias = _ref.alias;
        return resolve(alias);
      });
    });
  };

  this.refreshAlias = function () {
    return chrome.runtime.sendMessage({
      refreshAlias: true
    });
  };

  this.isDeviceSignedIn = function () {
    return _this.getAlias();
  };

  this.trySigningIn = function () {
    if (isDDGDomain()) {
      sendAndWaitForAnswer(SIGN_IN_MSG, 'addUserData').then(function (data) {
        return _this.storeUserData(data);
      });
    }
  };

  this.storeUserData = function (data) {
    return chrome.runtime.sendMessage(data);
  };

  this.addDeviceListeners = function () {
    // Add contextual menu listeners
    var activeEl = null;
    document.addEventListener('contextmenu', function (e) {
      activeEl = e.target;
    });
    chrome.runtime.onMessage.addListener(function (message, sender) {
      if (sender.id !== chrome.runtime.id) return;

      switch (message.type) {
        case 'ddgUserReady':
          scanForInputs(_this);
          break;

        case 'contextualAutofill':
          setValue(activeEl, message.alias);
          activeEl.classList.add('ddg-autofilled');

          _this.refreshAlias(); // If the user changes the alias, remove the decoration


          activeEl.addEventListener('input', function (e) {
            return e.target.classList.remove('ddg-autofilled');
          }, {
            once: true
          });
          break;

        default:
          break;
      }
    });
  };

  this.addLogoutListener = function (handler) {
    // Cleanup on logout events
    chrome.runtime.onMessage.addListener(function (message, sender) {
      if (sender.id === chrome.runtime.id && message.type === 'logout') {
        handler();
      }
    });
  };

  this.attachTooltip = createAttachTooltip(this.getAlias, this.refreshAlias);
};

var AndroidInterface = function AndroidInterface() {
  var _this2 = this;

  _classCallCheck(this, AndroidInterface);

  this.getAlias = function () {
    return sendAndWaitForAnswer(function () {
      return window.EmailInterface.showTooltip();
    }, 'getAliasResponse').then(function (_ref2) {
      var alias = _ref2.alias;
      return alias;
    });
  };

  this.refreshAlias = function () {};

  this.isDeviceSignedIn = function () {
    return new Promise(function (resolve) {
      return resolve(window.EmailInterface.isSignedIn() === 'true');
    });
  };

  this.trySigningIn = function () {
    if (isDDGDomain()) {
      sendAndWaitForAnswer(SIGN_IN_MSG, 'addUserData').then(function (data) {
        // This call doesn't send a response, so we can't know if it succeded
        _this2.storeUserData(data);

        scanForInputs(_this2);
      });
    }
  };

  this.storeUserData = function (_ref3) {
    var _ref3$addUserData = _ref3.addUserData,
        token = _ref3$addUserData.token,
        userName = _ref3$addUserData.userName;
    return window.EmailInterface.storeCredentials(token, userName);
  };

  this.addDeviceListeners = function () {};

  this.addLogoutListener = function () {};

  this.attachTooltip = createAttachTooltip(this.getAlias);
};

var AppleDeviceInterface = function AppleDeviceInterface() {
  var _this3 = this;

  _classCallCheck(this, AppleDeviceInterface);

  if (isDDGDomain()) {
    // Tell the web app whether we're in the macOS app
    notifyWebApp({
      isMacOSApp: isMacOSApp
    });
  }

  this.getAlias = function () {
    return sendAndWaitForAnswer(function () {
      return window.webkit.messageHandlers['emailHandlerGetAlias'].postMessage({
        requiresUserPermission: !isMacOSApp,
        shouldConsumeAliasIfProvided: !isMacOSApp
      });
    }, 'getAliasResponse').then(function (_ref4) {
      var alias = _ref4.alias;
      return alias;
    });
  };

  this.refreshAlias = function () {
    return window.webkit.messageHandlers['emailHandlerRefreshAlias'].postMessage({});
  };

  this.isDeviceSignedIn = function () {
    return sendAndWaitForAnswer(function () {
      return window.webkit.messageHandlers['emailHandlerCheckAppSignedInStatus'].postMessage({});
    }, 'checkExtensionSignedInCallback').then(function (data) {
      return data.isAppSignedIn;
    });
  };

  this.trySigningIn = function () {
    if (isDDGDomain()) {
      sendAndWaitForAnswer(SIGN_IN_MSG, 'addUserData').then(function (data) {
        // This call doesn't send a response, so we can't know if it succeded
        _this3.storeUserData(data);

        scanForInputs(_this3);
      });
    }
  };

  this.storeUserData = function (_ref5) {
    var _ref5$addUserData = _ref5.addUserData,
        token = _ref5$addUserData.token,
        userName = _ref5$addUserData.userName;
    return window.webkit.messageHandlers['emailHandlerStoreToken'].postMessage({
      token: token,
      username: userName
    });
  };

  this.addDeviceListeners = function () {
    window.addEventListener('message', function (e) {
      if (e.origin !== window.origin) return;

      if (e.data.ddgUserReady) {
        scanForInputs(_this3);
      }
    });
  };

  this.addLogoutListener = function () {};

  this.attachTooltip = createAttachTooltip(this.getAlias, this.refreshAlias);
};

var DeviceInterface = function () {
  if (isDDGApp) {
    return isAndroid ? new AndroidInterface() : new AppleDeviceInterface();
  }

  return new ExtensionInterface();
}();

module.exports = DeviceInterface;

},{"./DDGAutofill":3,"./autofill-utils":7,"./scanForInputs.js":11}],5:[function(require,module,exports){
"use strict";

function _createForOfIteratorHelper(o, allowArrayLike) { var it; if (typeof Symbol === "undefined" || o[Symbol.iterator] == null) { if (Array.isArray(o) || (it = _unsupportedIterableToArray(o)) || allowArrayLike && o && typeof o.length === "number") { if (it) o = it; var i = 0; var F = function F() {}; return { s: F, n: function n() { if (i >= o.length) return { done: true }; return { done: false, value: o[i++] }; }, e: function e(_e) { throw _e; }, f: F }; } throw new TypeError("Invalid attempt to iterate non-iterable instance.\nIn order to be iterable, non-array objects must have a [Symbol.iterator]() method."); } var normalCompletion = true, didErr = false, err; return { s: function s() { it = o[Symbol.iterator](); }, n: function n() { var step = it.next(); normalCompletion = step.done; return step; }, e: function e(_e2) { didErr = true; err = _e2; }, f: function f() { try { if (!normalCompletion && it["return"] != null) it["return"](); } finally { if (didErr) throw err; } } }; }

function _unsupportedIterableToArray(o, minLen) { if (!o) return; if (typeof o === "string") return _arrayLikeToArray(o, minLen); var n = Object.prototype.toString.call(o).slice(8, -1); if (n === "Object" && o.constructor) n = o.constructor.name; if (n === "Map" || n === "Set") return Array.from(o); if (n === "Arguments" || /^(?:Ui|I)nt(?:8|16|32)(?:Clamped)?Array$/.test(n)) return _arrayLikeToArray(o, minLen); }

function _arrayLikeToArray(arr, len) { if (len == null || len > arr.length) len = arr.length; for (var i = 0, arr2 = new Array(len); i < len; i++) { arr2[i] = arr[i]; } return arr2; }

function _classCallCheck(instance, Constructor) { if (!(instance instanceof Constructor)) { throw new TypeError("Cannot call a class as a function"); } }

function _defineProperties(target, props) { for (var i = 0; i < props.length; i++) { var descriptor = props[i]; descriptor.enumerable = descriptor.enumerable || false; descriptor.configurable = true; if ("value" in descriptor) descriptor.writable = true; Object.defineProperty(target, descriptor.key, descriptor); } }

function _createClass(Constructor, protoProps, staticProps) { if (protoProps) _defineProperties(Constructor.prototype, protoProps); if (staticProps) _defineProperties(Constructor, staticProps); return Constructor; }

var FormAnalyzer = require('./FormAnalyzer');

var _require = require('./autofill-utils'),
    addInlineStyles = _require.addInlineStyles,
    removeInlineStyles = _require.removeInlineStyles;

var _require2 = require('./logo-svg'),
    daxBase64 = _require2.daxBase64;

var _require3 = require('./autofill-utils'),
    isDDGApp = _require3.isDDGApp,
    setValue = _require3.setValue,
    isEventWithinDax = _require3.isEventWithinDax;

var getDaxImg = isDDGApp ? daxBase64 : chrome.runtime.getURL('img/logo-small.svg');

var getDaxStyles = function getDaxStyles(input) {
  return {
    // Height must be > 0 to account for fields initially hidden
    'background-size': "auto ".concat(input.offsetHeight <= 30 && input.offsetHeight > 0 ? '100%' : '24px'),
    'background-position': 'center right',
    'background-repeat': 'no-repeat',
    'background-origin': 'content-box',
    'background-image': "url(".concat(getDaxImg, ")")
  };
};

var INLINE_AUTOFILLED_STYLES = {
  'background-color': '#F8F498',
  'color': '#333333'
};

var Form = /*#__PURE__*/function () {
  function Form(form, input, attachTooltip) {
    var _this = this;

    _classCallCheck(this, Form);

    this.form = form;
    this.formAnalyzer = new FormAnalyzer(form, input);
    this.attachTooltip = attachTooltip;
    this.relevantInputs = new Set();
    this.touched = new Set();
    this.listeners = new Set();
    this.addInput(input);
    this.tooltip = null;
    this.activeInput = null;
    this.intObs = new IntersectionObserver(function (entries) {
      var _iterator = _createForOfIteratorHelper(entries),
          _step;

      try {
        for (_iterator.s(); !(_step = _iterator.n()).done;) {
          var entry = _step.value;
          if (!entry.isIntersecting) _this.removeTooltip();
        }
      } catch (err) {
        _iterator.e(err);
      } finally {
        _iterator.f();
      }
    });

    this.removeTooltip = function (e) {
      if (e && (e.target === _this.activeInput || e.target === _this.tooltip.host)) {
        return;
      }

      _this.tooltip.remove();

      _this.tooltip = null;

      _this.intObs.disconnect();

      window.removeEventListener('mousedown', _this.removeTooltip, {
        capture: true
      });
    };

    this.removeInputHighlight = function (input) {
      removeInlineStyles(input, INLINE_AUTOFILLED_STYLES);
      input.classList.remove('ddg-autofilled');
    };

    this.removeAllHighlights = function (e) {
      // This ensures we are not removing the highlight ourselves when autofilling more than once
      if (e && !e.isTrusted) return;

      _this.execOnInputs(_this.removeInputHighlight);
    };

    this.removeInputDecoration = function (input) {
      removeInlineStyles(input, getDaxStyles(input));
      input.removeAttribute('data-ddg-autofill');
    };

    this.removeAllDecorations = function () {
      _this.execOnInputs(_this.removeInputDecoration);

      _this.listeners.forEach(function (_ref) {
        var el = _ref.el,
            type = _ref.type,
            fn = _ref.fn;
        return el.removeEventListener(type, fn);
      });
    };

    this.resetAllInputs = function () {
      _this.execOnInputs(function (input) {
        setValue(input, '');

        _this.removeInputHighlight(input);
      });

      if (_this.activeInput) _this.activeInput.focus();
    };

    this.dismissTooltip = function () {
      _this.resetAllInputs();

      _this.removeTooltip();
    };

    return this;
  }

  _createClass(Form, [{
    key: "execOnInputs",
    value: function execOnInputs(fn) {
      this.relevantInputs.forEach(fn);
    }
  }, {
    key: "addInput",
    value: function addInput(input) {
      this.relevantInputs.add(input);
      if (this.formAnalyzer.autofillSignal > 0) this.decorateInput(input);
      return this;
    }
  }, {
    key: "areAllInputsEmpty",
    value: function areAllInputsEmpty() {
      var allEmpty = true;
      this.execOnInputs(function (input) {
        if (input.value) allEmpty = false;
      });
      return allEmpty;
    }
  }, {
    key: "addListener",
    value: function addListener(el, type, fn) {
      el.addEventListener(type, fn);
      this.listeners.add({
        el: el,
        type: type,
        fn: fn
      });
    }
  }, {
    key: "decorateInput",
    value: function decorateInput(input) {
      var _this2 = this;

      input.setAttribute('data-ddg-autofill', 'true');
      addInlineStyles(input, getDaxStyles(input));
      this.addListener(input, 'mousemove', function (e) {
        if (isEventWithinDax(e, e.target)) {
          e.target.style.setProperty('cursor', 'pointer', 'important');
        } else {
          e.target.style.removeProperty('cursor');
        }
      });
      this.addListener(input, 'mousedown', function (e) {
        if (!e.isTrusted) return;
        if (e.button !== 0) return;

        if (_this2.shouldOpenTooltip(e, e.target)) {
          e.preventDefault();
          e.stopImmediatePropagation();

          _this2.touched.add(e.target);

          _this2.attachTooltip(_this2, e.target);
        }
      });
      return this;
    }
  }, {
    key: "shouldOpenTooltip",
    value: function shouldOpenTooltip(e, input) {
      return !this.touched.has(input) && this.areAllInputsEmpty() || isEventWithinDax(e, input);
    }
  }, {
    key: "autofill",
    value: function autofill(alias) {
      var _this3 = this;

      this.execOnInputs(function (input) {
        setValue(input, alias);
        input.classList.add('ddg-autofilled');
        addInlineStyles(input, INLINE_AUTOFILLED_STYLES); // If the user changes the alias, remove the decoration

        input.addEventListener('input', _this3.removeAllHighlights, {
          once: true
        });
      });

      if (this.tooltip) {
        this.removeTooltip();
      }
    }
  }]);

  return Form;
}();

module.exports = Form;

},{"./FormAnalyzer":6,"./autofill-utils":7,"./logo-svg":9}],6:[function(require,module,exports){
"use strict";

function _classCallCheck(instance, Constructor) { if (!(instance instanceof Constructor)) { throw new TypeError("Cannot call a class as a function"); } }

function _defineProperties(target, props) { for (var i = 0; i < props.length; i++) { var descriptor = props[i]; descriptor.enumerable = descriptor.enumerable || false; descriptor.configurable = true; if ("value" in descriptor) descriptor.writable = true; Object.defineProperty(target, descriptor.key, descriptor); } }

function _createClass(Constructor, protoProps, staticProps) { if (protoProps) _defineProperties(Constructor.prototype, protoProps); if (staticProps) _defineProperties(Constructor, staticProps); return Constructor; }

var FormAnalyzer = /*#__PURE__*/function () {
  function FormAnalyzer(form, input) {
    _classCallCheck(this, FormAnalyzer);

    this.form = form;
    this.autofillSignal = 0;
    this.signals = []; // Avoid autofill on our signup page

    if (window.location.href.match(/^https:\/\/.+\.duckduckgo\.com\/email\/signup/i)) return this;
    this.evaluateElAttributes(input, 3, true);
    form ? this.evaluateForm() : this.evaluatePage();
    console.log(this.autofillSignal, this, this.signals);
    return this;
  }

  _createClass(FormAnalyzer, [{
    key: "increaseSignalBy",
    value: function increaseSignalBy(strength, signal) {
      this.autofillSignal += strength;
      this.signals.push("".concat(signal, ": +").concat(strength));
      return this;
    }
  }, {
    key: "decreaseSignalBy",
    value: function decreaseSignalBy(strength, signal) {
      this.autofillSignal -= strength;
      this.signals.push("".concat(signal, ": -").concat(strength));
      return this;
    }
  }, {
    key: "updateSignal",
    value: function updateSignal(_ref) {
      var string = _ref.string,
          strength = _ref.strength,
          _ref$signalType = _ref.signalType,
          signalType = _ref$signalType === void 0 ? 'generic' : _ref$signalType,
          _ref$shouldFlip = _ref.shouldFlip,
          shouldFlip = _ref$shouldFlip === void 0 ? false : _ref$shouldFlip,
          _ref$shouldCheckUnifi = _ref.shouldCheckUnifiedForm,
          shouldCheckUnifiedForm = _ref$shouldCheckUnifi === void 0 ? false : _ref$shouldCheckUnifi,
          _ref$shouldBeConserva = _ref.shouldBeConservative,
          shouldBeConservative = _ref$shouldBeConserva === void 0 ? false : _ref$shouldBeConserva;
      var negativeRegex = new RegExp(/sign(ing)?.?in(?!g)|log.?in/i);
      var positiveRegex = new RegExp(/sign(ing)?.?up|join|regist(er|ration)|newsletter|subscri(be|ption)|contact|create|start|settings|preferences|profile|update|checkout|guest|purchase|buy|order/i);
      var conservativePositiveRegex = new RegExp(/sign.?up|join|register|newsletter|subscri(be|ption)|settings|preferences|profile|update/i);
      var strictPositiveRegex = new RegExp(/sign.?up|join|register|settings|preferences|profile|update/i);
      var matchesNegative = string.match(negativeRegex); // Check explicitly for unified login/signup forms. They should always be negative, so we increase signal

      if (shouldCheckUnifiedForm && matchesNegative && string.match(strictPositiveRegex)) {
        this.decreaseSignalBy(strength + 2, "Unified detected ".concat(signalType));
        return this;
      }

      var matchesPositive = string.match(shouldBeConservative ? conservativePositiveRegex : positiveRegex); // In some cases a login match means the login is somewhere else, i.e. when a link points outside

      if (shouldFlip) {
        if (matchesNegative) this.increaseSignalBy(strength, signalType);
        if (matchesPositive) this.decreaseSignalBy(strength, signalType);
      } else {
        if (matchesNegative) this.decreaseSignalBy(strength, signalType);
        if (matchesPositive) this.increaseSignalBy(strength, signalType);
      }

      return this;
    }
  }, {
    key: "evaluateElAttributes",
    value: function evaluateElAttributes(el) {
      var _this = this;

      var signalStrength = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : 3;
      var isInput = arguments.length > 2 && arguments[2] !== undefined ? arguments[2] : false;
      Array.from(el.attributes).forEach(function (attr) {
        var attributeString = "".concat(attr.nodeName, "=").concat(attr.nodeValue);

        _this.updateSignal({
          string: attributeString,
          strength: signalStrength,
          signalType: "".concat(el.nodeName, " attr: ").concat(attributeString),
          shouldCheckUnifiedForm: isInput
        });
      });
    }
  }, {
    key: "evaluatePageTitle",
    value: function evaluatePageTitle() {
      var pageTitle = document.title;
      this.updateSignal({
        string: pageTitle,
        strength: 2,
        signalType: "page title: ".concat(pageTitle)
      });
    }
  }, {
    key: "evaluatePageHeadings",
    value: function evaluatePageHeadings() {
      var _this2 = this;

      var headings = document.querySelectorAll('h1, h2, h3, [class*="title"], [id*="title"]');

      if (headings) {
        headings.forEach(function (_ref2) {
          var innerText = _ref2.innerText;

          _this2.updateSignal({
            string: innerText,
            strength: 0.5,
            signalType: "heading: ".concat(innerText),
            shouldCheckUnifiedForm: true,
            shouldBeConservative: true
          });
        });
      }
    }
  }, {
    key: "evaluatePage",
    value: function evaluatePage() {
      var _this3 = this;

      this.evaluatePageTitle();
      this.evaluatePageHeadings(); // Check for submit buttons

      var buttons = document.querySelectorAll("\n                button[type=submit],\n                button:not([type]),\n                [role=button]\n            ");
      buttons.forEach(function (button) {
        // if the button has a form, it's not related to our input, because our input has no form here
        if (!button.form && !button.closest('form')) {
          _this3.evaluateElement(button);

          _this3.evaluateElAttributes(button, 0.5);
        }
      });
    }
  }, {
    key: "elementIs",
    value: function elementIs(el, type) {
      return el.nodeName.toLowerCase() === type.toLowerCase();
    }
  }, {
    key: "getText",
    value: function getText(el) {
      var _this4 = this;

      // for buttons, we don't care about descendants, just get the whole text as is
      // this is important in order to give proper attribution of the text to the button
      if (this.elementIs(el, 'BUTTON')) return el.innerText;
      if (this.elementIs(el, 'INPUT') && ['submit', 'button'].includes(el.type)) return el.value;
      return Array.from(el.childNodes).reduce(function (text, child) {
        return _this4.elementIs(child, '#text') ? text + ' ' + child.textContent : text;
      }, '');
    }
  }, {
    key: "evaluateElement",
    value: function evaluateElement(el) {
      var string = this.getText(el); // check button contents

      if (this.elementIs(el, 'INPUT') && ['submit', 'button'].includes(el.type) || this.elementIs(el, 'BUTTON') && el.type === 'submit' || (el.getAttribute('role') || '').toUpperCase() === 'BUTTON') {
        this.updateSignal({
          string: string,
          strength: 2,
          signalType: "submit: ".concat(string)
        });
      } // if a link points to relevant urls or contain contents outside the page…


      if (this.elementIs(el, 'A') && el.href && el.href !== '#' || (el.getAttribute('role') || '').toUpperCase() === 'LINK') {
        // …and matches one of the regexes, we assume the match is not pertinent to the current form
        this.updateSignal({
          string: string,
          strength: 1,
          signalType: "external link: ".concat(string),
          shouldFlip: true
        });
      } else {
        // any other case
        this.updateSignal({
          string: string,
          strength: 1,
          signalType: "generic: ".concat(string),
          shouldCheckUnifiedForm: true
        });
      }
    }
  }, {
    key: "evaluateForm",
    value: function evaluateForm() {
      var _this5 = this;

      // Check page title
      this.evaluatePageTitle(); // Check form attributes

      this.evaluateElAttributes(this.form); // Check form contents (skip select and option because they contain too much noise)

      this.form.querySelectorAll('*:not(select):not(option)').forEach(function (el) {
        return _this5.evaluateElement(el);
      }); // If we can't decide at this point, try reading page headings

      if (this.autofillSignal === 0) {
        this.evaluatePageHeadings();
      }

      return this;
    }
  }]);

  return FormAnalyzer;
}();

module.exports = FormAnalyzer;

},{}],7:[function(require,module,exports){
"use strict";

function _slicedToArray(arr, i) { return _arrayWithHoles(arr) || _iterableToArrayLimit(arr, i) || _unsupportedIterableToArray(arr, i) || _nonIterableRest(); }

function _nonIterableRest() { throw new TypeError("Invalid attempt to destructure non-iterable instance.\nIn order to be iterable, non-array objects must have a [Symbol.iterator]() method."); }

function _iterableToArrayLimit(arr, i) { if (typeof Symbol === "undefined" || !(Symbol.iterator in Object(arr))) return; var _arr = []; var _n = true; var _d = false; var _e = undefined; try { for (var _i = arr[Symbol.iterator](), _s; !(_n = (_s = _i.next()).done); _n = true) { _arr.push(_s.value); if (i && _arr.length === i) break; } } catch (err) { _d = true; _e = err; } finally { try { if (!_n && _i["return"] != null) _i["return"](); } finally { if (_d) throw _e; } } return _arr; }

function _arrayWithHoles(arr) { if (Array.isArray(arr)) return arr; }

function _createForOfIteratorHelper(o, allowArrayLike) { var it; if (typeof Symbol === "undefined" || o[Symbol.iterator] == null) { if (Array.isArray(o) || (it = _unsupportedIterableToArray(o)) || allowArrayLike && o && typeof o.length === "number") { if (it) o = it; var i = 0; var F = function F() {}; return { s: F, n: function n() { if (i >= o.length) return { done: true }; return { done: false, value: o[i++] }; }, e: function e(_e2) { throw _e2; }, f: F }; } throw new TypeError("Invalid attempt to iterate non-iterable instance.\nIn order to be iterable, non-array objects must have a [Symbol.iterator]() method."); } var normalCompletion = true, didErr = false, err; return { s: function s() { it = o[Symbol.iterator](); }, n: function n() { var step = it.next(); normalCompletion = step.done; return step; }, e: function e(_e3) { didErr = true; err = _e3; }, f: function f() { try { if (!normalCompletion && it["return"] != null) it["return"](); } finally { if (didErr) throw err; } } }; }

function _unsupportedIterableToArray(o, minLen) { if (!o) return; if (typeof o === "string") return _arrayLikeToArray(o, minLen); var n = Object.prototype.toString.call(o).slice(8, -1); if (n === "Object" && o.constructor) n = o.constructor.name; if (n === "Map" || n === "Set") return Array.from(o); if (n === "Arguments" || /^(?:Ui|I)nt(?:8|16|32)(?:Clamped)?Array$/.test(n)) return _arrayLikeToArray(o, minLen); }

function _arrayLikeToArray(arr, len) { if (len == null || len > arr.length) len = arr.length; for (var i = 0, arr2 = new Array(len); i < len; i++) { arr2[i] = arr[i]; } return arr2; }

var isMacOSApp = false; // Do not modify or remove the next line -- the macOS app code will replace it with `isMacOSApp = true;`
// INJECT isMacOSApp HERE

var isDDGApp = /(iPhone|iPad|Android|Mac).*DuckDuckGo\/[0-9]/i.test(window.navigator.userAgent) || isMacOSApp;
var isAndroid = isDDGApp && /Android/i.test(window.navigator.userAgent);
var DDG_DOMAIN_REGEX = new RegExp(/^https:\/\/(([a-z0-9-_]+?)\.)?duckduckgo\.com/);

var isDDGDomain = function isDDGDomain() {
  return window.origin.match(DDG_DOMAIN_REGEX);
}; // Send a message to the web app (only on DDG domains)


var notifyWebApp = function notifyWebApp(message) {
  if (isDDGDomain()) {
    window.postMessage(message, window.origin);
  }
};
/**
 * Sends a message and returns a Promise that resolves with the response
 * @param {{} | Function} msgOrFn - a fn to call or an object to send via postMessage
 * @param {String} expectedResponse - the name of the response
 * @returns {Promise<unknown>}
 */


var sendAndWaitForAnswer = function sendAndWaitForAnswer(msgOrFn, expectedResponse) {
  if (typeof msgOrFn === 'function') {
    msgOrFn();
  } else {
    window.postMessage(msgOrFn, window.origin);
  }

  return new Promise(function (resolve) {
    var handler = function handler(e) {
      if (e.origin !== window.origin) return;
      if (!e.data || e.data && !(e.data[expectedResponse] || e.data.type === expectedResponse)) return;
      resolve(e.data);
      window.removeEventListener('message', handler);
    };

    window.addEventListener('message', handler);
  });
}; // Access the original setter (needed to bypass React's implementation on mobile)


var originalSet = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set; // This ensures that the value is set properly and dispatches events to simulate a real user action

var setValue = function setValue(el, val) {
  // Avoid keyboard flashing on Android
  if (!isAndroid) {
    el.focus();
  }

  originalSet.call(el, val);
  var ev = new Event('input', {
    bubbles: true
  });
  el.dispatchEvent(ev);
  el.blur();
};
/**
 * Use IntersectionObserver v2 to make sure the element is visible when clicked
 * https://developers.google.com/web/updates/2019/02/intersectionobserver-v2
 */


var safeExecute = function safeExecute(el, fn) {
  var intObs = new IntersectionObserver(function (changes) {
    var _iterator = _createForOfIteratorHelper(changes),
        _step;

    try {
      for (_iterator.s(); !(_step = _iterator.n()).done;) {
        var change = _step.value;

        // Feature detection
        if (typeof change.isVisible === 'undefined') {
          // The browser doesn't support Intersection Observer v2, falling back to v1 behavior.
          change.isVisible = true;
        }

        if (change.isIntersecting && change.isVisible) {
          fn();
        }
      }
    } catch (err) {
      _iterator.e(err);
    } finally {
      _iterator.f();
    }

    intObs.disconnect();
  }, {
    trackVisibility: true,
    delay: 100
  });
  intObs.observe(el);
};

var getDaxBoundingBox = function getDaxBoundingBox(input) {
  var _input$getBoundingCli = input.getBoundingClientRect(),
      inputRight = _input$getBoundingCli.right,
      inputTop = _input$getBoundingCli.top,
      inputHeight = _input$getBoundingCli.height;

  var inputRightPadding = parseInt(getComputedStyle(input).paddingRight);
  var width = 30;
  var height = 30;
  var top = inputTop + (inputHeight - height) / 2;
  var right = inputRight - inputRightPadding;
  var left = right - width;
  var bottom = top + height;
  return {
    bottom: bottom,
    height: height,
    left: left,
    right: right,
    top: top,
    width: width,
    x: left,
    y: top
  };
};

var isEventWithinDax = function isEventWithinDax(e, input) {
  var _getDaxBoundingBox = getDaxBoundingBox(input),
      left = _getDaxBoundingBox.left,
      right = _getDaxBoundingBox.right,
      top = _getDaxBoundingBox.top,
      bottom = _getDaxBoundingBox.bottom;

  var withinX = e.clientX >= left && e.clientX <= right;
  var withinY = e.clientY >= top && e.clientY <= bottom;
  return withinX && withinY;
};

var addInlineStyles = function addInlineStyles(el, styles) {
  return Object.entries(styles).forEach(function (_ref) {
    var _ref2 = _slicedToArray(_ref, 2),
        property = _ref2[0],
        val = _ref2[1];

    return el.style.setProperty(property, val, 'important');
  });
};

var removeInlineStyles = function removeInlineStyles(el, styles) {
  return Object.keys(styles).forEach(function (property) {
    return el.style.removeProperty(property);
  });
};

module.exports = {
  isMacOSApp: isMacOSApp,
  isDDGApp: isDDGApp,
  isAndroid: isAndroid,
  DDG_DOMAIN_REGEX: DDG_DOMAIN_REGEX,
  isDDGDomain: isDDGDomain,
  notifyWebApp: notifyWebApp,
  sendAndWaitForAnswer: sendAndWaitForAnswer,
  setValue: setValue,
  safeExecute: safeExecute,
  getDaxBoundingBox: getDaxBoundingBox,
  isEventWithinDax: isEventWithinDax,
  addInlineStyles: addInlineStyles,
  removeInlineStyles: removeInlineStyles
};

},{}],8:[function(require,module,exports){
"use strict";

(function () {
  // Polyfills/shims
  require('intersection-observer');

  require('./requestIdleCallback');

  var DeviceInterface = require('./DeviceInterface');

  var scanForInputs = require('./scanForInputs.js');

  DeviceInterface.addDeviceListeners();
  DeviceInterface.isDeviceSignedIn().then(function (deviceSignedIn) {
    if (deviceSignedIn) {
      scanForInputs(DeviceInterface);
    } else {
      DeviceInterface.trySigningIn();
    }
  });
})();

},{"./DeviceInterface":4,"./requestIdleCallback":10,"./scanForInputs.js":11,"intersection-observer":1}],9:[function(require,module,exports){
"use strict";

var daxSvg = '<svg fill="none" height="24" viewBox="0 0 44 44" width="24" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink"><linearGradient id="a"><stop offset=".01" stop-color="#6176b9"/><stop offset=".69" stop-color="#394a9f"/></linearGradient><linearGradient id="b" gradientUnits="userSpaceOnUse" x1="13.9297" x2="17.072" xlink:href="#a" y1="16.398" y2="16.398"/><linearGradient id="c" gradientUnits="userSpaceOnUse" x1="23.8115" x2="26.6752" xlink:href="#a" y1="14.9679" y2="14.9679"/><mask id="d" height="40" maskUnits="userSpaceOnUse" width="40" x="2" y="2"><path clip-rule="evenodd" d="m22.0003 41.0669c10.5302 0 19.0666-8.5364 19.0666-19.0666 0-10.5303-8.5364-19.06671-19.0666-19.06671-10.5303 0-19.06671 8.53641-19.06671 19.06671 0 10.5302 8.53641 19.0666 19.06671 19.0666z" fill="#fff" fill-rule="evenodd"/></mask><path clip-rule="evenodd" d="m22 44c12.1503 0 22-9.8497 22-22 0-12.15026-9.8497-22-22-22-12.15026 0-22 9.84974-22 22 0 12.1503 9.84974 22 22 22z" fill="#de5833" fill-rule="evenodd"/><g mask="url(#d)"><path clip-rule="evenodd" d="m26.0813 41.6386c-.9203-1.7893-1.8003-3.4356-2.3466-4.5246-1.452-2.9077-2.9114-7.007-2.2477-9.6507.121-.4803-1.3677-17.78699-2.42-18.34432-1.1697-.62333-3.7107-1.44467-5.027-1.66467-.9167-.14666-1.1257.11-1.5107.16867.363.03667 2.09.88733 2.4237.935-.3337.22733-1.32-.00733-1.9507.27133-.319.14667-.5573.68934-.55.946 1.7967-.18333 4.6054-.00366 6.27.73329-1.3236.1504-3.333.319-4.1983.7737-2.508 1.32-3.6153 4.411-2.9553 8.1143.6563 3.696 3.564 17.1784 4.4916 21.681.924 4.499 11.5537 3.5567 10.0174.561z" fill="#d5d7d8" fill-rule="evenodd"/><path d="m22.2865 26.8439c-.66 2.6436.792 6.7393 2.2476 9.6506.4891.9727 1.2438 2.3921 2.0558 3.9637-1.894.4693-6.4895 1.1264-9.7191 0-.924-4.4917-3.8317-17.9777-4.4953-21.681-.66-3.7033 0-6.347 2.5153-7.667.8617-.4547 2.0937-.7847 3.4137-.9313-1.6647-.7407-3.6374-1.0267-5.4414-.84336-.0073-.76267 1.3384-.71867 1.8444-1.06334-.3337-.04766-1.1624-.79566-1.529-.83233 2.2883-.39244 4.6423-.02138 6.699 1.056 1.0486.561 1.7893 1.16233 2.2476 1.79303 1.1954.2273 2.2514.66 2.9407 1.3493 2.1193 2.1157 4.0113 6.952 3.2193 9.7313-.2236.77-.7333 1.331-1.3713 1.7967-1.2393.902-1.0193-1.045-4.103.9717-.3997.2603-.3997 2.2256-.5243 2.706z" fill="#fff"/></g><g clip-rule="evenodd" fill-rule="evenodd"><path d="m16.6724 20.354c.7675 0 1.3896-.6221 1.3896-1.3896s-.6221-1.3897-1.3896-1.3897-1.3897.6222-1.3897 1.3897.6222 1.3896 1.3897 1.3896z" fill="#2d4f8e"/><path d="m17.2924 18.8617c.1985 0 .3594-.1608.3594-.3593s-.1609-.3593-.3594-.3593c-.1984 0-.3593.1608-.3593.3593s.1609.3593.3593.3593z" fill="#fff"/><path d="m25.9568 19.3311c.6581 0 1.1917-.5335 1.1917-1.1917 0-.6581-.5336-1.1916-1.1917-1.1916s-1.1917.5335-1.1917 1.1916c0 .6582.5336 1.1917 1.1917 1.1917z" fill="#2d4f8e"/><path d="m26.4882 18.0511c.1701 0 .308-.1379.308-.308s-.1379-.308-.308-.308-.308.1379-.308.308.1379.308.308.308z" fill="#fff"/><path d="m17.072 14.942s-1.0486-.4766-2.0643.165c-1.0157.638-.979 1.2907-.979 1.2907s-.539-1.2027.8983-1.793c1.441-.5867 2.145.3373 2.145.3373z" fill="url(#b)"/><path d="m26.6752 14.8467s-.7517-.429-1.3383-.4217c-1.199.0147-1.5254.5427-1.5254.5427s.2017-1.2614 1.7344-1.0084c.4997.0914.9223.4234 1.1293.8874z" fill="url(#c)"/><path d="m20.9258 24.321c.1393-.8433 2.31-2.431 3.85-2.53 1.54-.0953 2.0167-.0733 3.3-.3813 1.287-.3043 4.598-1.1293 5.511-1.5547.9167-.4216 4.8033.209 2.0643 1.738-1.1843.6637-4.378 1.881-6.6623 2.563-2.2807.682-3.663-.6526-4.422.4694-.6013.891-.121 2.112 2.6033 2.365 3.6814.341 7.2087-1.6574 7.5974-.594.3886 1.0633-3.1607 2.3833-5.324 2.4273-2.1634.0403-6.5194-1.43-7.172-1.8847-.6564-.451-1.5254-1.5143-1.3457-2.618z" fill="#fdd20a"/><path d="m28.8825 31.8386c-.7773-.1724-4.312 2.5006-4.312 2.5006h.0037l-.165 2.0534s4.0406 1.6536 4.73 1.397c.6893-.264.517-5.775-.2567-5.951zm-11.5463 1.034c.0843-1.1184 5.2543 1.6426 5.2543 1.6426l.0037-.0036.2566 2.156s-4.3083 2.5813-4.9133 2.2366c-.6013-.3446-.6893-4.9096-.6013-6.0316z" fill="#65bc46"/><path d="m21.34 34.8049c0 1.8077-.2604 2.585.5133 2.7574.7773.1723 2.2403 0 2.761-.3447.5133-.3447.0843-2.6693-.088-3.102s-3.19-.088-3.19.6893z" fill="#43a244"/><path d="m21.6701 34.4051c0 1.8076-.2604 2.5813.5133 2.7536.7737.176 2.2367 0 2.7573-.3446.517-.3447.088-2.6694-.0843-3.102-.1723-.4327-3.19-.0844-3.19.6893z" fill="#65bc46"/><path d="m22.0002 40.4481c10.1885 0 18.4479-8.2594 18.4479-18.4479s-8.2594-18.44795-18.4479-18.44795-18.44795 8.25945-18.44795 18.44795 8.25945 18.4479 18.44795 18.4479zm0 1.7187c11.1377 0 20.1666-9.0289 20.1666-20.1666 0-11.1378-9.0289-20.1667-20.1666-20.1667-11.1378 0-20.1667 9.0289-20.1667 20.1667 0 11.1377 9.0289 20.1666 20.1667 20.1666z" fill="#fff"/></g></svg>';
var daxBase64 = 'data:image/svg+xml;base64,PHN2ZyBmaWxsPSJub25lIiBoZWlnaHQ9IjI0IiB2aWV3Qm94PSIwIDAgNDQgNDQiIHdpZHRoPSIyNCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIiB4bWxuczp4bGluaz0iaHR0cDovL3d3dy53My5vcmcvMTk5OS94bGluayI+PGxpbmVhckdyYWRpZW50IGlkPSJhIj48c3RvcCBvZmZzZXQ9Ii4wMSIgc3RvcC1jb2xvcj0iIzYxNzZiOSIvPjxzdG9wIG9mZnNldD0iLjY5IiBzdG9wLWNvbG9yPSIjMzk0YTlmIi8+PC9saW5lYXJHcmFkaWVudD48bGluZWFyR3JhZGllbnQgaWQ9ImIiIGdyYWRpZW50VW5pdHM9InVzZXJTcGFjZU9uVXNlIiB4MT0iMTMuOTI5NyIgeDI9IjE3LjA3MiIgeGxpbms6aHJlZj0iI2EiIHkxPSIxNi4zOTgiIHkyPSIxNi4zOTgiLz48bGluZWFyR3JhZGllbnQgaWQ9ImMiIGdyYWRpZW50VW5pdHM9InVzZXJTcGFjZU9uVXNlIiB4MT0iMjMuODExNSIgeDI9IjI2LjY3NTIiIHhsaW5rOmhyZWY9IiNhIiB5MT0iMTQuOTY3OSIgeTI9IjE0Ljk2NzkiLz48bWFzayBpZD0iZCIgaGVpZ2h0PSI0MCIgbWFza1VuaXRzPSJ1c2VyU3BhY2VPblVzZSIgd2lkdGg9IjQwIiB4PSIyIiB5PSIyIj48cGF0aCBjbGlwLXJ1bGU9ImV2ZW5vZGQiIGQ9Im0yMi4wMDAzIDQxLjA2NjljMTAuNTMwMiAwIDE5LjA2NjYtOC41MzY0IDE5LjA2NjYtMTkuMDY2NiAwLTEwLjUzMDMtOC41MzY0LTE5LjA2NjcxLTE5LjA2NjYtMTkuMDY2NzEtMTAuNTMwMyAwLTE5LjA2NjcxIDguNTM2NDEtMTkuMDY2NzEgMTkuMDY2NzEgMCAxMC41MzAyIDguNTM2NDEgMTkuMDY2NiAxOS4wNjY3MSAxOS4wNjY2eiIgZmlsbD0iI2ZmZiIgZmlsbC1ydWxlPSJldmVub2RkIi8+PC9tYXNrPjxwYXRoIGNsaXAtcnVsZT0iZXZlbm9kZCIgZD0ibTIyIDQ0YzEyLjE1MDMgMCAyMi05Ljg0OTcgMjItMjIgMC0xMi4xNTAyNi05Ljg0OTctMjItMjItMjItMTIuMTUwMjYgMC0yMiA5Ljg0OTc0LTIyIDIyIDAgMTIuMTUwMyA5Ljg0OTc0IDIyIDIyIDIyeiIgZmlsbD0iI2RlNTgzMyIgZmlsbC1ydWxlPSJldmVub2RkIi8+PGcgbWFzaz0idXJsKCNkKSI+PHBhdGggY2xpcC1ydWxlPSJldmVub2RkIiBkPSJtMjYuMDgxMyA0MS42Mzg2Yy0uOTIwMy0xLjc4OTMtMS44MDAzLTMuNDM1Ni0yLjM0NjYtNC41MjQ2LTEuNDUyLTIuOTA3Ny0yLjkxMTQtNy4wMDctMi4yNDc3LTkuNjUwNy4xMjEtLjQ4MDMtMS4zNjc3LTE3Ljc4Njk5LTIuNDItMTguMzQ0MzItMS4xNjk3LS42MjMzMy0zLjcxMDctMS40NDQ2Ny01LjAyNy0xLjY2NDY3LS45MTY3LS4xNDY2Ni0xLjEyNTcuMTEtMS41MTA3LjE2ODY3LjM2My4wMzY2NyAyLjA5Ljg4NzMzIDIuNDIzNy45MzUtLjMzMzcuMjI3MzMtMS4zMi0uMDA3MzMtMS45NTA3LjI3MTMzLS4zMTkuMTQ2NjctLjU1NzMuNjg5MzQtLjU1Ljk0NiAxLjc5NjctLjE4MzMzIDQuNjA1NC0uMDAzNjYgNi4yNy43MzMyOS0xLjMyMzYuMTUwNC0zLjMzMy4zMTktNC4xOTgzLjc3MzctMi41MDggMS4zMi0zLjYxNTMgNC40MTEtMi45NTUzIDguMTE0My42NTYzIDMuNjk2IDMuNTY0IDE3LjE3ODQgNC40OTE2IDIxLjY4MS45MjQgNC40OTkgMTEuNTUzNyAzLjU1NjcgMTAuMDE3NC41NjF6IiBmaWxsPSIjZDVkN2Q4IiBmaWxsLXJ1bGU9ImV2ZW5vZGQiLz48cGF0aCBkPSJtMjIuMjg2NSAyNi44NDM5Yy0uNjYgMi42NDM2Ljc5MiA2LjczOTMgMi4yNDc2IDkuNjUwNi40ODkxLjk3MjcgMS4yNDM4IDIuMzkyMSAyLjA1NTggMy45NjM3LTEuODk0LjQ2OTMtNi40ODk1IDEuMTI2NC05LjcxOTEgMC0uOTI0LTQuNDkxNy0zLjgzMTctMTcuOTc3Ny00LjQ5NTMtMjEuNjgxLS42Ni0zLjcwMzMgMC02LjM0NyAyLjUxNTMtNy42NjcuODYxNy0uNDU0NyAyLjA5MzctLjc4NDcgMy40MTM3LS45MzEzLTEuNjY0Ny0uNzQwNy0zLjYzNzQtMS4wMjY3LTUuNDQxNC0uODQzMzYtLjAwNzMtLjc2MjY3IDEuMzM4NC0uNzE4NjcgMS44NDQ0LTEuMDYzMzQtLjMzMzctLjA0NzY2LTEuMTYyNC0uNzk1NjYtMS41MjktLjgzMjMzIDIuMjg4My0uMzkyNDQgNC42NDIzLS4wMjEzOCA2LjY5OSAxLjA1NiAxLjA0ODYuNTYxIDEuNzg5MyAxLjE2MjMzIDIuMjQ3NiAxLjc5MzAzIDEuMTk1NC4yMjczIDIuMjUxNC42NiAyLjk0MDcgMS4zNDkzIDIuMTE5MyAyLjExNTcgNC4wMTEzIDYuOTUyIDMuMjE5MyA5LjczMTMtLjIyMzYuNzctLjczMzMgMS4zMzEtMS4zNzEzIDEuNzk2Ny0xLjIzOTMuOTAyLTEuMDE5My0xLjA0NS00LjEwMy45NzE3LS4zOTk3LjI2MDMtLjM5OTcgMi4yMjU2LS41MjQzIDIuNzA2eiIgZmlsbD0iI2ZmZiIvPjwvZz48ZyBjbGlwLXJ1bGU9ImV2ZW5vZGQiIGZpbGwtcnVsZT0iZXZlbm9kZCI+PHBhdGggZD0ibTE2LjY3MjQgMjAuMzU0Yy43Njc1IDAgMS4zODk2LS42MjIxIDEuMzg5Ni0xLjM4OTZzLS42MjIxLTEuMzg5Ny0xLjM4OTYtMS4zODk3LTEuMzg5Ny42MjIyLTEuMzg5NyAxLjM4OTcuNjIyMiAxLjM4OTYgMS4zODk3IDEuMzg5NnoiIGZpbGw9IiMyZDRmOGUiLz48cGF0aCBkPSJtMTcuMjkyNCAxOC44NjE3Yy4xOTg1IDAgLjM1OTQtLjE2MDguMzU5NC0uMzU5M3MtLjE2MDktLjM1OTMtLjM1OTQtLjM1OTNjLS4xOTg0IDAtLjM1OTMuMTYwOC0uMzU5My4zNTkzcy4xNjA5LjM1OTMuMzU5My4zNTkzeiIgZmlsbD0iI2ZmZiIvPjxwYXRoIGQ9Im0yNS45NTY4IDE5LjMzMTFjLjY1ODEgMCAxLjE5MTctLjUzMzUgMS4xOTE3LTEuMTkxNyAwLS42NTgxLS41MzM2LTEuMTkxNi0xLjE5MTctMS4xOTE2cy0xLjE5MTcuNTMzNS0xLjE5MTcgMS4xOTE2YzAgLjY1ODIuNTMzNiAxLjE5MTcgMS4xOTE3IDEuMTkxN3oiIGZpbGw9IiMyZDRmOGUiLz48cGF0aCBkPSJtMjYuNDg4MiAxOC4wNTExYy4xNzAxIDAgLjMwOC0uMTM3OS4zMDgtLjMwOHMtLjEzNzktLjMwOC0uMzA4LS4zMDgtLjMwOC4xMzc5LS4zMDguMzA4LjEzNzkuMzA4LjMwOC4zMDh6IiBmaWxsPSIjZmZmIi8+PHBhdGggZD0ibTE3LjA3MiAxNC45NDJzLTEuMDQ4Ni0uNDc2Ni0yLjA2NDMuMTY1Yy0xLjAxNTcuNjM4LS45NzkgMS4yOTA3LS45NzkgMS4yOTA3cy0uNTM5LTEuMjAyNy44OTgzLTEuNzkzYzEuNDQxLS41ODY3IDIuMTQ1LjMzNzMgMi4xNDUuMzM3M3oiIGZpbGw9InVybCgjYikiLz48cGF0aCBkPSJtMjYuNjc1MiAxNC44NDY3cy0uNzUxNy0uNDI5LTEuMzM4My0uNDIxN2MtMS4xOTkuMDE0Ny0xLjUyNTQuNTQyNy0xLjUyNTQuNTQyN3MuMjAxNy0xLjI2MTQgMS43MzQ0LTEuMDA4NGMuNDk5Ny4wOTE0LjkyMjMuNDIzNCAxLjEyOTMuODg3NHoiIGZpbGw9InVybCgjYykiLz48cGF0aCBkPSJtMjAuOTI1OCAyNC4zMjFjLjEzOTMtLjg0MzMgMi4zMS0yLjQzMSAzLjg1LTIuNTMgMS41NC0uMDk1MyAyLjAxNjctLjA3MzMgMy4zLS4zODEzIDEuMjg3LS4zMDQzIDQuNTk4LTEuMTI5MyA1LjUxMS0xLjU1NDcuOTE2Ny0uNDIxNiA0LjgwMzMuMjA5IDIuMDY0MyAxLjczOC0xLjE4NDMuNjYzNy00LjM3OCAxLjg4MS02LjY2MjMgMi41NjMtMi4yODA3LjY4Mi0zLjY2My0uNjUyNi00LjQyMi40Njk0LS42MDEzLjg5MS0uMTIxIDIuMTEyIDIuNjAzMyAyLjM2NSAzLjY4MTQuMzQxIDcuMjA4Ny0xLjY1NzQgNy41OTc0LS41OTQuMzg4NiAxLjA2MzMtMy4xNjA3IDIuMzgzMy01LjMyNCAyLjQyNzMtMi4xNjM0LjA0MDMtNi41MTk0LTEuNDMtNy4xNzItMS44ODQ3LS42NTY0LS40NTEtMS41MjU0LTEuNTE0My0xLjM0NTctMi42MTh6IiBmaWxsPSIjZmRkMjBhIi8+PHBhdGggZD0ibTI4Ljg4MjUgMzEuODM4NmMtLjc3NzMtLjE3MjQtNC4zMTIgMi41MDA2LTQuMzEyIDIuNTAwNmguMDAzN2wtLjE2NSAyLjA1MzRzNC4wNDA2IDEuNjUzNiA0LjczIDEuMzk3Yy42ODkzLS4yNjQuNTE3LTUuNzc1LS4yNTY3LTUuOTUxem0tMTEuNTQ2MyAxLjAzNGMuMDg0My0xLjExODQgNS4yNTQzIDEuNjQyNiA1LjI1NDMgMS42NDI2bC4wMDM3LS4wMDM2LjI1NjYgMi4xNTZzLTQuMzA4MyAyLjU4MTMtNC45MTMzIDIuMjM2NmMtLjYwMTMtLjM0NDYtLjY4OTMtNC45MDk2LS42MDEzLTYuMDMxNnoiIGZpbGw9IiM2NWJjNDYiLz48cGF0aCBkPSJtMjEuMzQgMzQuODA0OWMwIDEuODA3Ny0uMjYwNCAyLjU4NS41MTMzIDIuNzU3NC43NzczLjE3MjMgMi4yNDAzIDAgMi43NjEtLjM0NDcuNTEzMy0uMzQ0Ny4wODQzLTIuNjY5My0uMDg4LTMuMTAycy0zLjE5LS4wODgtMy4xOS42ODkzeiIgZmlsbD0iIzQzYTI0NCIvPjxwYXRoIGQ9Im0yMS42NzAxIDM0LjQwNTFjMCAxLjgwNzYtLjI2MDQgMi41ODEzLjUxMzMgMi43NTM2Ljc3MzcuMTc2IDIuMjM2NyAwIDIuNzU3My0uMzQ0Ni41MTctLjM0NDcuMDg4LTIuNjY5NC0uMDg0My0zLjEwMi0uMTcyMy0uNDMyNy0zLjE5LS4wODQ0LTMuMTkuNjg5M3oiIGZpbGw9IiM2NWJjNDYiLz48cGF0aCBkPSJtMjIuMDAwMiA0MC40NDgxYzEwLjE4ODUgMCAxOC40NDc5LTguMjU5NCAxOC40NDc5LTE4LjQ0NzlzLTguMjU5NC0xOC40NDc5NS0xOC40NDc5LTE4LjQ0Nzk1LTE4LjQ0Nzk1IDguMjU5NDUtMTguNDQ3OTUgMTguNDQ3OTUgOC4yNTk0NSAxOC40NDc5IDE4LjQ0Nzk1IDE4LjQ0Nzl6bTAgMS43MTg3YzExLjEzNzcgMCAyMC4xNjY2LTkuMDI4OSAyMC4xNjY2LTIwLjE2NjYgMC0xMS4xMzc4LTkuMDI4OS0yMC4xNjY3LTIwLjE2NjYtMjAuMTY2Ny0xMS4xMzc4IDAtMjAuMTY2NyA5LjAyODktMjAuMTY2NyAyMC4xNjY3IDAgMTEuMTM3NyA5LjAyODkgMjAuMTY2NiAyMC4xNjY3IDIwLjE2NjZ6IiBmaWxsPSIjZmZmIi8+PC9nPjwvc3ZnPg==';
module.exports = {
  daxSvg: daxSvg,
  daxBase64: daxBase64
};

},{}],10:[function(require,module,exports){
"use strict";

/*!
 * Copyright 2015 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

/*
 * @see https://developers.google.com/web/updates/2015/08/using-requestidlecallback
 */
window.requestIdleCallback = window.requestIdleCallback || function (cb) {
  return setTimeout(function () {
    var start = Date.now(); // eslint-disable-next-line standard/no-callback-literal

    cb({
      didTimeout: false,
      timeRemaining: function timeRemaining() {
        return Math.max(0, 50 - (Date.now() - start));
      }
    });
  }, 1);
};

window.cancelIdleCallback = window.cancelIdleCallback || function (id) {
  clearTimeout(id);
};

},{}],11:[function(require,module,exports){
"use strict";

function _createForOfIteratorHelper(o, allowArrayLike) { var it; if (typeof Symbol === "undefined" || o[Symbol.iterator] == null) { if (Array.isArray(o) || (it = _unsupportedIterableToArray(o)) || allowArrayLike && o && typeof o.length === "number") { if (it) o = it; var i = 0; var F = function F() {}; return { s: F, n: function n() { if (i >= o.length) return { done: true }; return { done: false, value: o[i++] }; }, e: function e(_e) { throw _e; }, f: F }; } throw new TypeError("Invalid attempt to iterate non-iterable instance.\nIn order to be iterable, non-array objects must have a [Symbol.iterator]() method."); } var normalCompletion = true, didErr = false, err; return { s: function s() { it = o[Symbol.iterator](); }, n: function n() { var step = it.next(); normalCompletion = step.done; return step; }, e: function e(_e2) { didErr = true; err = _e2; }, f: function f() { try { if (!normalCompletion && it["return"] != null) it["return"](); } finally { if (didErr) throw err; } } }; }

function _unsupportedIterableToArray(o, minLen) { if (!o) return; if (typeof o === "string") return _arrayLikeToArray(o, minLen); var n = Object.prototype.toString.call(o).slice(8, -1); if (n === "Object" && o.constructor) n = o.constructor.name; if (n === "Map" || n === "Set") return Array.from(o); if (n === "Arguments" || /^(?:Ui|I)nt(?:8|16|32)(?:Clamped)?Array$/.test(n)) return _arrayLikeToArray(o, minLen); }

function _arrayLikeToArray(arr, len) { if (len == null || len > arr.length) len = arr.length; for (var i = 0, arr2 = new Array(len); i < len; i++) { arr2[i] = arr[i]; } return arr2; }

var Form = require('./Form');

var _require = require('./autofill-utils'),
    notifyWebApp = _require.notifyWebApp; // Accepts the DeviceInterface as an explicit dependency


var scanForInputs = function scanForInputs(DeviceInterface) {
  notifyWebApp({
    deviceSignedIn: {
      value: true
    }
  });
  var forms = new Map();
  var EMAIL_SELECTOR = "\n            input:not([type])[name*=mail i]:not([readonly]):not([disabled]):not([hidden]):not([aria-hidden=true]),\n            input[type=\"\"][name*=mail i]:not([readonly]):not([disabled]):not([hidden]):not([aria-hidden=true]),\n            input[type=text][name*=mail i]:not([readonly]):not([disabled]):not([hidden]):not([aria-hidden=true]),\n            input:not([type])[id*=mail i]:not([readonly]):not([disabled]):not([hidden]):not([aria-hidden=true]),\n            input:not([type])[placeholder*=mail i]:not([readonly]):not([disabled]):not([hidden]):not([aria-hidden=true]),\n            input[type=\"\"][id*=mail i]:not([readonly]):not([disabled]):not([hidden]):not([aria-hidden=true]),\n            input[type=text][placeholder*=mail i]:not([readonly]):not([disabled]):not([hidden]):not([aria-hidden=true]),\n            input[type=\"\"][placeholder*=mail i]:not([readonly]):not([disabled]):not([hidden]):not([aria-hidden=true]),\n            input:not([type])[placeholder*=mail i]:not([readonly]):not([disabled]):not([hidden]):not([aria-hidden=true]),\n            input[type=email]:not([readonly]):not([disabled]):not([hidden]):not([aria-hidden=true]),\n            input[type=text][aria-label*=mail i],\n            input:not([type])[aria-label*=mail i],\n            input[type=text][placeholder*=mail i]:not([readonly])\n        ";

  var addInput = function addInput(input) {
    var parentForm = input.form;

    if (forms.has(parentForm)) {
      // If we've already met the form, add the input
      forms.get(parentForm).addInput(input);
    } else {
      forms.set(parentForm || input, new Form(parentForm, input, DeviceInterface.attachTooltip));
    }
  };

  var findEligibleInput = function findEligibleInput(context) {
    if (context.nodeName === 'INPUT' && context.matches(EMAIL_SELECTOR)) {
      addInput(context);
    } else {
      context.querySelectorAll(EMAIL_SELECTOR).forEach(addInput);
    }
  };

  findEligibleInput(document); // For all DOM mutations, search for new eligible inputs and update existing inputs positions

  var mutObs = new MutationObserver(function (mutationList) {
    var _iterator = _createForOfIteratorHelper(mutationList),
        _step;

    try {
      for (_iterator.s(); !(_step = _iterator.n()).done;) {
        var mutationRecord = _step.value;

        if (mutationRecord.type === 'childList') {
          // We query only within the context of added/removed nodes
          mutationRecord.addedNodes.forEach(function (el) {
            if (el.nodeName === 'DDG-AUTOFILL') return;

            if (el instanceof HTMLElement) {
              window.requestIdleCallback(function () {
                findEligibleInput(el);
              });
            }
          });
        }
      }
    } catch (err) {
      _iterator.e(err);
    } finally {
      _iterator.f();
    }
  });
  mutObs.observe(document.body, {
    childList: true,
    subtree: true
  });

  var logoutHandler = function logoutHandler() {
    // remove Dax, listeners, and observers
    mutObs.disconnect();
    forms.forEach(function (form) {
      form.resetAllInputs();
      form.removeAllDecorations();
    });
    forms.clear();
    notifyWebApp({
      deviceSignedIn: {
        value: false
      }
    });
  };

  DeviceInterface.addLogoutListener(logoutHandler);
};

module.exports = scanForInputs;

},{"./Form":5,"./autofill-utils":7}]},{},[8]);
