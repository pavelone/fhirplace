'use strict'

angular.module('fhirplaceSpaUi')
  .controller 'MetadataIndexCtrl', ($scope, $http) ->
    $http({method: 'GET', url: 'http://localhost:8889/metadata?_format=application/json'}).
      success((data, status, headers, config) ->
        $scope.name = data.name
        $scope.resources = data.rest[0].resources
        console.log data
      ).
      error (data, status, headers, config) ->
        # called asynchronously if an error occurs
        # or server returns response with an error status.
        console.log 'karamba'
