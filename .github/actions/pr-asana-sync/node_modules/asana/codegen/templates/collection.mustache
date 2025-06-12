/**
 * Create a Collection object from a response containing a list of resources.
 *
 * @param {Object} response_and_data
 * @param {ApiClient} apiClient
 * @param {Object} apiRequestData
 * @returns {Object} Collection
 */
function Collection(response_and_data, apiClient, apiRequestData) {
    if (!Collection.isCollectionResponse(response_and_data.data.data)) {
    throw new Error(
        'Cannot create Collection from response that does not have resources');
    }
    
    this.data = response_and_data.data.data; // return the contents inside of the "data" key that Asana API returns
    this._response = response_and_data.data;
    this._apiClient = apiClient;
    this._apiRequestData = apiRequestData;
}

/**
 * Transforms a Promise of a raw response into a Promise for a Collection.
 *
 * @param {Promise<Object>} promise
 * @param {ApiClient} apiClient
 * @param {Object} apiRequestData
 * @returns {Promise<Collection>}
 */
Collection.fromApiClient = function(promise, apiClient, apiRequestData) {
    return promise.then(function(response_and_data) {
        return new Collection(response_and_data, apiClient, apiRequestData);
    });
};

/**
 * @param response {Object} Response that a request promise resolved to
 * @returns {boolean} True iff the response is a collection (possibly empty)
 */
Collection.isCollectionResponse = function(responseData) {
    return typeof(responseData) === 'object' &&
        typeof(responseData) === 'object' &&
        typeof(responseData.length) === 'number';
};

module.exports = Collection;

/**
 * Get the next page of results in a collection.
 *
 * @returns {Promise<Collection?>} Resolves to either a collection representing
 *     the next page of results, or null if no more pages.
 */
Collection.prototype.nextPage = function() {
    /* jshint camelcase:false */
    var me = this;
    var next = me._response.next_page;
    var apiRequestData = me._apiRequestData;
    if (typeof(next) === 'object' && next !== null && me.data && me.data.length > 0) {
        apiRequestData.queryParams['offset'] = next.offset;
        return Collection.fromApiClient(
            me._apiClient.callApi(
                apiRequestData.path,
                apiRequestData.httpMethod,
                apiRequestData.pathParams,
                apiRequestData.queryParams,
                apiRequestData.headerParams,
                apiRequestData.formParams,
                apiRequestData.bodyParam,
                apiRequestData.authNames,
                apiRequestData.contentTypes,
                apiRequestData.accepts,
                apiRequestData.returnType
            ),
            me._apiClient,
            me._apiRequestData);
    } else {
        // No more results.
        return Promise.resolve({"data": null});
    }
};
