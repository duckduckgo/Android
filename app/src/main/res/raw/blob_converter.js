//
//  blob_converter.js
//  DuckDuckGo
//
//  Copyright © 2020 DuckDuckGo. All rights reserved.
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//

(function() {
    var xhr = new XMLHttpRequest();
    xhr.open('GET', '%blobUrl%', true);
    xhr.setRequestHeader('Content-type','%contentType%');
    xhr.responseType = 'blob';
    xhr.onload = function(e) {
        if (this.status == 200) {
            var blob = this.response;
            var reader = new FileReader();
            reader.readAsDataURL(blob);
            reader.onloadend = function() {
                dataUrl = reader.result;
                BlobConverter.convertBlobToDataUri(dataUrl, '%contentType%');
            }
            reader.onerror = function() {
                BlobConverter.convertBlobToDataUri('error', '%contentType%');
            }
        } else {
            BlobConverter.convertBlobToDataUri('error', '%contentType%');
        }
    };
    xhr.send();
})();