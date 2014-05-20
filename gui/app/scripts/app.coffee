'use strict'

angular.module('fhirplaceGui', [
  'ngCookies',
  'ngResource',
  'ngSanitize',
  'ngRoute',
  'formstamp'
])
  .config ($routeProvider) ->

    $routeProvider
      .when '/',
        templateUrl: 'views/metadata/index.html'
        controller: 'MetadataIndexCtrl'
      .when '/resources/:resourceType',
        templateUrl: 'views/resources/index.html'
        controller: 'ResourcesIndexCtrl'
      .when '/resources/:resourceType/new',
        templateUrl: 'views/resources/new.html'
        controller: 'ResourcesNewCtrl'
      .when '/resources/:resourceType/:resourceId',
        templateUrl: 'views/resources/edit.html'
        controller: 'ResourcesEditCtrl'
      .otherwise
        redirectTo: '/'