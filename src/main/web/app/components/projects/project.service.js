(function () {
    'use strict';

    angular.module('fims.projects')
        .factory('ProjectService', ProjectService);

    ProjectService.$inject = ['$q', '$rootScope', '$cacheFactory', '$http', '$timeout', 'StorageService', 'ProjectConfigService', 'REST_ROOT'];

    function ProjectService($q, $rootScope, $cacheFactory, $http, $timeout, StorageService, ProjectConfigService, REST_ROOT) {
        var PROJECT_CACHE = $cacheFactory('project');

        var _loading = false;

        var service = {
            currentProject: undefined,
            set: set,
            setFromId: setFromId,
            waitForProject: waitForProject,
            all: all
        };

        $rootScope.$on("$logoutEvent", function () {
            if (service.currentProject) {
                var projectId = service.currentProject.projectId;
                service.currentProject = undefined;
                // this will set the project only if it is a public project
                setFromId(projectId);
            }

        });

        return service;

        /**
         * Returns a Promise that is resolved when the project loads, or after 2s. If the project is loaded,
         * the promise will resolve immediately. The promise will be rejected if there is no project loaded.
         */
        function waitForProject() {
            if (_loading) {
                return $q(function(resolve, reject) {
                    $rootScope.$on('$projectChangeEvent', function (project) {
                        resolve(project);
                    });

                    // set a timeout in-case the project takes too long to load
                    $timeout(function () {
                        if (service.currentProject) {
                            resolve(service.currentProject);
                        } else {
                            reject();
                        }
                    }, 2000, false);
                });
            } else if (service.currentProject) {
                return $q.when(service.currentProject);
            } else {
                return $q.reject();
            }
        }

        function set(project) {
            _loading = true;
            ProjectConfigService.get(project.projectId)
                .then(function (config) {
                    service.currentProject.config = config;
                    StorageService.set('projectId', service.currentProject.projectId);
                    $rootScope.$broadcast('$projectChangeEvent', service.currentProject);
                    _loading = false;
                }, function (response) {
                    _loading = false;
                    //TODO handle error
                });

            service.currentProject = project;
        }

        function setFromId(projectId) {
            _loading = true;
            return all(true)
                .then(function (response) {
                    var found = false;
                    for (var i = 0; i < response.data.length; i++) {
                        if (response.data[i].projectId == projectId) {
                            set(response.data[i]);
                            found = true;
                            break;
                        }
                    }
                    _loading = false;
                }, function () {
                    _loading = false;
                });
        }

        function all(includePublic) {
            return $http.get(REST_ROOT + 'projects?includePublic=' + includePublic, {cache: PROJECT_CACHE});
        }
    }
})();