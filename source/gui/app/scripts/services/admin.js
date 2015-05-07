'use strict';

/**
 * @ngdoc service
 * @name sat5TelemetryApp.Admin
 * @description
 * # Systems
 * Service in the sat5TelemetryApp.
 */
angular.module('sat5TelemetryApp')
.factory('Admin', function (
$http, 
EVENTS, 
CONFIG_URLS, 
HTTP_CONST,
CONFIG_KEYS,
ADMIN_TABS) {

  var _tab = ADMIN_TABS.GENERAL;
  var _enabled = false;
  var _configenabled = false;
  var _username = "";
  var _systems = [];
  var _systemStatuses = [];
  var _pageSize = 10;
  var _page = 0;
  var _filteredSystemLength = 0;
  var _filteredSystems = [];
  var _validSystems = [];


  var setTab = function(tab) {
    _tab = tab;
  };

  var getTab = function() {
    return _tab;
  };

  var generalTabSelected = function() {
    return _tab === ADMIN_TABS.GENERAL;
  };

  var systemsTabSelected = function() {
    return _tab === ADMIN_TABS.SYSTEMS;
  };

  var getEnabled = function() {
    return _enabled;
  };

  var setEnabled = function(enabled) {
    _enabled = enabled;
  };

  var getConfigEnabled = function() {
    return _configenabled;
  };

  var setConfigenabled = function(configenabled) {
    _configenabled = configenabled;
  };

  var getUsername = function() {
    return _username;
  };

  var setUsername = function(username) {
    _username = username;
  };

  var setSystems = function(systems) {
    _systems = systems;
  };

  var getSystems = function() {
    return _systems;
  };

  var setPageSize = function(pageSize) {
    _pageSize = pageSize;
  };

  var getPageSize = function() {
    return _pageSize;
  };

  var setPage = function(page) {
    _page = page;
  };

  var getPage = function() {
    return _page;
  };

  var getValidSystems = function() {
    return _.filter(getSystemStatuses(), {'validType': true});
  };

  var getNumSystems = function() {
    return _filteredSystemLength;
  };

  var updateSystemLength = function(newLength) {
    _filteredSystemLength = newLength;
  };

  var getPageStart = function() {
    return getPage() * getPageSize();
  };

  var setSystemStatuses = function(systemStatuses) {
    _systemStatuses = systemStatuses;
  };

  var addSystemStatuses = function(systemStatuses) {
    _systemStatuses = _.union(_systemStatuses, systemStatuses);
  }

  var getSystemStatus = function(system) {
    var status = _.where(_systemStatuses, {'id': system.id});
    return status[0];
  };

  var getSystemStatuses = function() {
    return _systemStatuses;
  };

  var setFilteredSystems = function(systems) {
    if (!_.isEqual(_.pluck(systems, 'id'), _.pluck(_filteredSystems, 'id'))) {
      _filteredSystems = systems;
      getStatuses();
    }
    _filteredSystems = systems;
  };

  var getFilteredSystems = function() {
    return _filteredSystems;
  };
  /**
   * Scrape the username off the page. 
   * Need this for calls to Sat5 API in the proxy.
   */
  var getSatelliteUser = function() {
    return $('nav.navbar-pf > div.navbar-collapse > ul.navbar-nav > li > a[href="/rhn/account/UserDetails.do"]').text().trim();
  };

  /**
   * args:
   *  - systems -> array of satellite system ids
   *
   * response:
   *  - array of satellite system ids missing installation status
   */
  var getSystemsMissingStatus = function(systems) {
    var missingSystems = _.difference(
      _.pluck(systems, 'id'),
      _.pluck(getSystemStatuses(), 'id')); 
    return missingSystems;
  };

  var postConfig = function(enabled, username, password, configenabled) {
    var headers = {};
    headers[HTTP_CONST.ACCEPT] = HTTP_CONST.APPLICATION_JSON;
    headers[HTTP_CONST.CONTENT_TYPE] = HTTP_CONST.APPLICATION_JSON;
    var params = {};
    params[CONFIG_KEYS.SATELLITE_USER] = getSatelliteUser();
    var data = {};
    data[CONFIG_KEYS.ENABLED] = enabled;
    data[CONFIG_KEYS.CONFIGENABLED] = configenabled;
    data[CONFIG_KEYS.USERNAME] = username;
    if (password !== null) {
      data[CONFIG_KEYS.PASSWORD] = password;
    }
    var promise = $http({
      method: HTTP_CONST.POST,
      url: CONFIG_URLS.GENERAL,
      headers: headers,
      data: data,
      params: params
    });
    return promise;
  };

  var getConfig = function() {
    var headers = {};
    headers[HTTP_CONST.ACCEPT] = HTTP_CONST.APPLICATION_JSON;
    var params = {};
    params[CONFIG_KEYS.SATELLITE_USER] = getSatelliteUser();
    var promise = $http({
      method: HTTP_CONST.GET,
      url: CONFIG_URLS.GENERAL,
      headers: headers,
      params: params
    });
    return promise;
  };

  var postSystems = function(systems) {
    var headers = {};
    headers[HTTP_CONST.ACCEPT] = HTTP_CONST.APPLICATION_JSON;
    headers[HTTP_CONST.CONTENT_TYPE] = HTTP_CONST.APPLICATION_JSON;
    var params = {};
    params[CONFIG_KEYS.SATELLITE_USER] = getSatelliteUser();
    var data = systems;
    var promise = $http({
      method: HTTP_CONST.POST,
      url: CONFIG_URLS.SYSTEMS,
      headers: headers,
      params: params,
      data: data
    });
    return promise;
  };

  var getSystemsPromise = function() {
    var headers = {};
    headers[HTTP_CONST.ACCEPT] = HTTP_CONST.APPLICATION_JSON;
    var params = {};
    params[CONFIG_KEYS.SATELLITE_USER] = getSatelliteUser();
    var promise = $http({
      method: HTTP_CONST.GET,
      url: CONFIG_URLS.SYSTEMS,
      headers: headers,
      params: params
    });
    return promise;
  };

  var getStatuses = function(systems) {
    var unknownSystems = getSystemsMissingStatus(getFilteredSystems());
    if (!_.isEmpty(unknownSystems)) {
      getStatusPromise(unknownSystems).then(
        function(statuses) {
          addSystemStatuses(statuses.data);
        });
    }
  };

  var getStatusPromise = function(systems) {
    var headers = {};
    headers[HTTP_CONST.ACCEPT] = HTTP_CONST.APPLICATION_JSON;
    headers[HTTP_CONST.CONTENT_TYPE] = HTTP_CONST.APPLICATION_JSON;
    var params = {};
    params[CONFIG_KEYS.SATELLITE_USER] = getSatelliteUser();
    params[CONFIG_KEYS.SYSTEMS] = systems.join();
    var promise = $http({
      method: HTTP_CONST.GET,
      url: CONFIG_URLS.STATUS,
      headers: headers,
      params: params
    });
    return promise;
  };

  return {
    postConfig: postConfig,
    getConfig: getConfig,
    setTab: setTab,
    getTab: getTab,
    generalTabSelected: generalTabSelected,
    systemsTabSelected: systemsTabSelected,
    getSystemsPromise: getSystemsPromise,
    postSystems: postSystems,
    getEnabled: getEnabled,
    setEnabled: setEnabled,
    getConfigEnabled: getConfigEnabled,
    setConfigenabled: setConfigenabled,
    setUsername: setUsername,
    getUsername: getUsername,
    getSystems: getSystems,
    setSystems: setSystems,
    getPageSize: getPageSize,
    setPageSize: setPageSize,
    getPage: getPage,
    setPage: setPage,
    getNumSystems: getNumSystems,
    getPageStart: getPageStart,
    updateSystemLength: updateSystemLength,
    getStatusPromise: getStatusPromise,
    getSystemStatus: getSystemStatus,
    getStatuses: getStatuses,
    setFilteredSystems: setFilteredSystems,
    getFilteredSystems: getFilteredSystems,
    getValidSystems: getValidSystems
  };
});
