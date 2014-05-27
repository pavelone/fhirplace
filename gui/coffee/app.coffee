'use strict'

app = angular.module 'fhirplaceGui', [
  'ngCookies',
  'ngAnimate',
  'ngSanitize',
  'ngRoute',
  'formstamp',
  "ui.codemirror"
], ($routeProvider) ->
    $routeProvider
      .when '/',
        templateUrl: '/views/welcome.html'
      .when '/conformance',
        templateUrl: '/views/conformance.html'
        controller: 'ConformanceCtrl'
      .when '/resources/:resourceType',
        templateUrl: '/views/resources/index.html'
        controller: 'ResourcesIndexCtrl'
      .when '/resources/:resourceType/new',
        templateUrl: '/views/resources/new.html'
        controller: 'ResourcesNewCtrl'
      .when '/resources/:resourceType/:resourceLogicalId',
        templateUrl: '/views/resources/show.html'
        controller: 'ResourceCtrl'
      .when '/resources/:resourceType/:resourceLogicalId/history',
        templateUrl: '/views/resources/history.html'
        controller: 'ResourcesHistoryCtrl'
      .otherwise
        redirectTo: '/'

defaultMenu = [{url: '/conformance', label: 'Conformance'}]
menu = (args...)->
  defaultMenu.slice(0).concat(args)

app.run ($rootScope)->
  $rootScope.menu = menu()
  $rootScope.$watch 'progress', (v)->
    return unless v && v.success
    delete $rootScope.error
    $rootScope.loading = 'Loading'
    $rootScope.progressCls = 'prgrss'
    v.success (vv)->
      $rootScope.loading = null
      console.log('progress success', vv)
      delete $rootScope.progressCls
     .error (vv)->
      $rootScope.loading = null
      $rootScope.error = vv
      console.log('progress error', vv)
      delete $rootScope.progressCls

cropUuid = (id)->
  sid = id.substring(id.length - 6, id.length)
  "...#{sid}"

app.filter 'uuid', ()-> cropUuid

keyComparator = (key)->
 (a, b) ->
   switch
     when a[key] < b[key] then -1
     when a[key] > b[key] then 1
     else 0


app.controller 'ConformanceCtrl', ($rootScope, $scope, $http) ->
  $scope.restRequestMethod = 'GET'
  $scope.restUri = '/metadata?_format=application/json'
  $rootScope.menu = menu()

  $rootScope.progress = $http.get($scope.restUri)
    .success (data, status, headers, config) ->
      $scope.resources = (data.rest[0] || []).resources.sort(keyComparator('type'))
      data.rest = null
      $scope.conformance = data
    .error (data, status, headers, config) ->
      console.log data


app.controller 'ResourcesIndexCtrl', ($rootScope, $scope, $routeParams, $http) ->
  angular.extend($scope, $routeParams)
  $scope.restRequestMethod = 'GET'
  rt = $scope.resourceType

  $scope.restUri = "/#{rt}/_search?_format=application/json"

  $rootScope.menu = menu(
    {url: "/resources/#{rt}", label: rt},
    {url: "/resources/#{rt}/new", label: "New", icon: "fa-plus"})

  $rootScope.progress = $http.get($scope.restUri).success (data, status, headers, config) ->
    $scope.resources = data.map (resource) ->
      resource.prettyData = angular.toJson(angular.fromJson(resource.data), true)
      resource

app.controller 'ResourcesNewCtrl', ($rootScope, $scope, $routeParams, $http, $location) ->
  angular.extend($scope, $routeParams)

  rt = $scope.resourceType

  $rootScope.menu = menu(
    {url: "/resources/#{rt}", label: rt},
    {url: "/resources/#{rt}/new", label: "New", icon: "fa-plus"})


  $scope.restRequestMethod = 'POST'
  $scope.restUri = "/#{$scope.resourceType}?_format=application/json"
  $scope.resource = {}

  headers = {'Content-Location': $scope.resourceContentLocation}
  $scope.save = ->
    $rootScope.progress = $http(method: $scope.restRequestMethod, url: $scope.restUri, data: $scope.resource.content, headers: headers)
      .success (data, status, headers, config) ->
        $location.path("/resources/#{$scope.resourceType}")

  $scope.validate = ()->
    url = "/#{rt}/_validate?_format=application/json"
    $rootScope.progress = $http.post(url, $scope.resource.content)
      .success (data)->
        console.log(data)

app.controller 'ResourceCtrl', ($rootScope, $scope, $routeParams, $http, $location) ->
  angular.extend($scope, $routeParams)
  $scope.restRequestMethod = 'GET'
  $scope.restUri = "/#{$scope.resourceType}/#{$scope.resourceLogicalId}?_format=application/json"

  rt = $scope.resourceType
  id = $scope.resourceLogicalId
  $rootScope.menu = menu(
    {url: "/resources/#{rt}", label: rt},
    {url: "/resources/#{rt}/#{id}", label: cropUuid(id)},
    {url: "/resources/#{rt}/#{id}/history", label: 'History', icon: 'fa-history'})

  loadResource = ()->
    $rootScope.progress = $http.get($scope.restUri).success (data, status, headers, config) ->
      $scope.resource = {
        content: angular.toJson(angular.fromJson(data), true)
      }
      $scope.resourceContentLocation = headers('content-location')

  loadResource()
  $scope.save = ->
    $http(method: "PUT", url: $scope.restUri, data: $scope.resource.content, headers: {'Content-Location': $scope.resourceContentLocation})
      .success (data, status, headers, config) ->
        loadResource()

  $scope.destroy = ->
    if window.confirm("Destroy #{$scope.resourceTypeLabel} #{$scope.resourceLabel}?")
      $http.delete($scope.restUri).success (data, status, headers, config) ->
        $location.path("/resources/#{$scope.resourceType}")

app.controller 'ResourcesHistoryCtrl', ($rootScope, $scope, $routeParams, $http) ->
  angular.extend($scope, $routeParams)
  $scope.restRequestMethod = 'GET'
  $scope.restUri =
    "/#{$scope.resourceType}/#{$scope.resourceLogicalId}/_history?_format=application/json"

  rt = $scope.resourceType
  id = $scope.resourceLogicalId
  $rootScope.menu = menu(
    {url: "/resources/#{rt}", label: rt},
    {url: "/resources/#{rt}/#{id}", label: cropUuid(id)},
    {url: "/resources/#{rt}/#{id}/history", label: 'History', icon: 'fa-history'})

  $rootScope.progress = $http.get($scope.restUri).success (data, status, headers, config) ->
    $scope.resourceHistory  = angular.toJson(angular.fromJson(data), true)
    $scope.resourceVersions = data.entry


# angular.module('fhirplaceGui')
#   .controller 'ResourcesValidateCtrl', ($scope, $http) ->
#     $scope.restRequestMethod = 'POST'
#     $scope.restUri =
#       "/#{$scope.resourceType}/_validate?_format=application/json"

#     $scope.validate = ->
#       if $scope.form.$valid
#         $scope.resourceValidation = 'Validating ...'
#         $http.post($scope.restUri, $scope.resource.prettyData)
#           .success((data, status, headers, config) ->
#             if data
#               $scope.resourceValidation = angular.toJson(
#                 angular.fromJson(data),
#                 true
#               )
#             else
#               $scope.resourceValidation = 'Everything is good'
#           ).error (data, status, headers, config) ->
#             $scope.resourceValidation = angular.toJson(
#               angular.fromJson(data),
#               true
#             )