"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.LocalUtilsDispatcher = void 0;

var _fs = _interopRequireDefault(require("fs"));

var _path = _interopRequireDefault(require("path"));

var _manualPromise = require("../../utils/manualPromise");

var _utils = require("../../utils");

var _dispatcher = require("./dispatcher");

var _zipBundle = require("../../zipBundle");

var _zipFile = require("../../utils/zipFile");

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

/**
 * Copyright (c) Microsoft Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
class LocalUtilsDispatcher extends _dispatcher.Dispatcher {
  constructor(scope) {
    super(scope, {
      guid: 'localUtils@' + (0, _utils.createGuid)()
    }, 'LocalUtils', {});
    this._type_LocalUtils = void 0;
    this._harBakends = new Map();
    this._type_LocalUtils = true;
  }

  async zip(params, metadata) {
    const promise = new _manualPromise.ManualPromise();
    const zipFile = new _zipBundle.yazl.ZipFile();
    zipFile.on('error', error => promise.reject(error));

    for (const entry of params.entries) {
      try {
        if (_fs.default.statSync(entry.value).isFile()) zipFile.addFile(entry.value, entry.name);
      } catch (e) {}
    }

    if (!_fs.default.existsSync(params.zipFile)) {
      // New file, just compress the entries.
      await _fs.default.promises.mkdir(_path.default.dirname(params.zipFile), {
        recursive: true
      });
      zipFile.end(undefined, () => {
        zipFile.outputStream.pipe(_fs.default.createWriteStream(params.zipFile)).on('close', () => promise.resolve());
      });
      return promise;
    } // File already exists. Repack and add new entries.


    const tempFile = params.zipFile + '.tmp';
    await _fs.default.promises.rename(params.zipFile, tempFile);

    _zipBundle.yauzl.open(tempFile, (err, inZipFile) => {
      if (err) {
        promise.reject(err);
        return;
      }

      (0, _utils.assert)(inZipFile);
      let pendingEntries = inZipFile.entryCount;
      inZipFile.on('entry', entry => {
        inZipFile.openReadStream(entry, (err, readStream) => {
          if (err) {
            promise.reject(err);
            return;
          }

          zipFile.addReadStream(readStream, entry.fileName);

          if (--pendingEntries === 0) {
            zipFile.end(undefined, () => {
              zipFile.outputStream.pipe(_fs.default.createWriteStream(params.zipFile)).on('close', () => {
                _fs.default.promises.unlink(tempFile).then(() => {
                  promise.resolve();
                });
              });
            });
          }
        });
      });
    });

    return promise;
  }

  async harOpen(params, metadata) {
    let harBackend;

    if (params.file.endsWith('.zip')) {
      const zipFile = new _zipFile.ZipFile(params.file);
      const entryNames = await zipFile.entries();
      const harEntryName = entryNames.find(e => e.endsWith('.har'));
      if (!harEntryName) return {
        error: 'Specified archive does not have a .har file'
      };
      const har = await zipFile.read(harEntryName);
      const harFile = JSON.parse(har.toString());
      harBackend = new HarBackend(harFile, null, zipFile);
    } else {
      const harFile = JSON.parse(await _fs.default.promises.readFile(params.file, 'utf-8'));
      harBackend = new HarBackend(harFile, _path.default.dirname(params.file), null);
    }

    this._harBakends.set(harBackend.id, harBackend);

    return {
      harId: harBackend.id
    };
  }

  async harLookup(params, metadata) {
    const harBackend = this._harBakends.get(params.harId);

    if (!harBackend) return {
      action: 'error',
      message: `Internal error: har was not opened`
    };
    return await harBackend.lookup(params.url, params.method, params.headers, params.postData ? Buffer.from(params.postData, 'base64') : undefined, params.isNavigationRequest);
  }

  async harClose(params, metadata) {
    const harBackend = this._harBakends.get(params.harId);

    if (harBackend) {
      this._harBakends.delete(harBackend.id);

      harBackend.dispose();
    }
  }

  async harUnzip(params, metadata) {
    const dir = _path.default.dirname(params.zipFile);

    const zipFile = new _zipFile.ZipFile(params.zipFile);

    for (const entry of await zipFile.entries()) {
      const buffer = await zipFile.read(entry);
      if (entry === 'har.har') await _fs.default.promises.writeFile(params.harFile, buffer);else await _fs.default.promises.writeFile(_path.default.join(dir, entry), buffer);
    }

    zipFile.close();
    await _fs.default.promises.unlink(params.zipFile);
  }

}

exports.LocalUtilsDispatcher = LocalUtilsDispatcher;
const redirectStatus = [301, 302, 303, 307, 308];

class HarBackend {
  constructor(harFile, baseDir, zipFile) {
    this.id = (0, _utils.createGuid)();
    this._harFile = void 0;
    this._zipFile = void 0;
    this._baseDir = void 0;
    this._harFile = harFile;
    this._baseDir = baseDir;
    this._zipFile = zipFile;
  }

  async lookup(url, method, headers, postData, isNavigationRequest) {
    let entry;

    try {
      entry = await this._harFindResponse(url, method, headers, postData);
    } catch (e) {
      return {
        action: 'error',
        message: 'HAR error: ' + e.message
      };
    }

    if (!entry) return {
      action: 'noentry'
    }; // If navigation is being redirected, restart it with the final url to ensure the document's url changes.

    if (entry.request.url !== url && isNavigationRequest) return {
      action: 'redirect',
      redirectURL: entry.request.url
    };
    const response = entry.response;

    try {
      const buffer = await this._loadContent(response.content);
      return {
        action: 'fulfill',
        status: response.status,
        headers: response.headers,
        body: buffer.toString('base64')
      };
    } catch (e) {
      return {
        action: 'error',
        message: e.message
      };
    }
  }

  async _loadContent(content) {
    const file = content._file;
    let buffer;

    if (file) {
      if (this._zipFile) buffer = await this._zipFile.read(file);else buffer = await _fs.default.promises.readFile(_path.default.resolve(this._baseDir, file));
    } else {
      buffer = Buffer.from(content.text || '', content.encoding === 'base64' ? 'base64' : 'utf-8');
    }

    return buffer;
  }

  async _harFindResponse(url, method, headers, postData) {
    const harLog = this._harFile.log;
    const visited = new Set();

    while (true) {
      const entries = [];

      for (const candidate of harLog.entries) {
        if (candidate.request.url !== url || candidate.request.method !== method) continue;

        if (method === 'POST' && postData && candidate.request.postData) {
          const buffer = await this._loadContent(candidate.request.postData);
          if (!buffer.equals(postData)) continue;
        }

        entries.push(candidate);
      }

      if (!entries.length) return;
      let entry = entries[0]; // Disambiguate using headers - then one with most matching headers wins.

      if (entries.length > 1) {
        const list = [];

        for (const candidate of entries) {
          const matchingHeaders = countMatchingHeaders(candidate.request.headers, headers);
          list.push({
            candidate,
            matchingHeaders
          });
        }

        list.sort((a, b) => b.matchingHeaders - a.matchingHeaders);
        entry = list[0].candidate;
      }

      if (visited.has(entry)) throw new Error(`Found redirect cycle for ${url}`);
      visited.add(entry); // Follow redirects.

      const locationHeader = entry.response.headers.find(h => h.name.toLowerCase() === 'location');

      if (redirectStatus.includes(entry.response.status) && locationHeader) {
        const locationURL = new URL(locationHeader.value, url);
        url = locationURL.toString();

        if ((entry.response.status === 301 || entry.response.status === 302) && method === 'POST' || entry.response.status === 303 && !['GET', 'HEAD'].includes(method)) {
          // HTTP-redirect fetch step 13 (https://fetch.spec.whatwg.org/#http-redirect-fetch)
          method = 'GET';
        }

        continue;
      }

      return entry;
    }
  }

  dispose() {
    var _this$_zipFile;

    (_this$_zipFile = this._zipFile) === null || _this$_zipFile === void 0 ? void 0 : _this$_zipFile.close();
  }

}

function countMatchingHeaders(harHeaders, headers) {
  const set = new Set(headers.map(h => h.name.toLowerCase() + ':' + h.value));
  let matches = 0;

  for (const h of harHeaders) {
    if (set.has(h.name.toLowerCase() + ':' + h.value)) ++matches;
  }

  return matches;
}