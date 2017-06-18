var app = angular.module('biscicolApp', [
    'ui.router',
    'ui.bootstrap',
    'fims.header',
    'fims.alerts',
    'fims.query',
    'fims.auth',
    'fims.templates',
    'fims.expeditions',
    'fims.validation',
    'fims.projects',
    'fims.users',
    'fims.modals',
    'fims.lookup',
    'fims.creator',
    'utils.autofocus',
    'ui.bootstrap.showErrors',
    'angularSpinner'
]);

app.run(['$http', '$rootScope', '$transitions', 'LoadingModal', function ($http, $rootScope, $transitions, LoadingModal) {
    $http.defaults.headers.common = {'Fims-App': 'Biscicol-Fims'};

    $rootScope.isEmpty = function (val) {
        return angular.equals({}, val);
    };

    $transitions.onStart({}, function (trans) {
        if (trans.$to().resolvables.length > 0) {
            LoadingModal.open();
        }
    });

    $transitions.onFinish({}, function () {
        LoadingModal.close();
    });

    $transitions.onError({}, function () {
        LoadingModal.close(true);
    });
}]);

// register an interceptor to convert objects to a form-data like string for $http data attributes and
// set the appropriate header
app.factory('postInterceptor', [
    function () {
        return {
            request: function (config) {
                // when uploading files with ng-file-upload, the content-type is undefined. The browser
                // will automatically set it to multipart/form-data if we leave it as undefined
                if ((config.method === "POST" || config.method === "PUT") && config.headers['Content-Type'] !== undefined && !config.keepJson) {
                    config.headers['Content-Type'] = 'application/x-www-form-urlencoded; charset=UTF-8';
                    if (config.data instanceof Object)
                        config.data = config.paramSerializer(config.data);
                }
                return config;
            }
        };
    }])

    .config(['$httpProvider', function ($httpProvider) {
        $httpProvider.interceptors.push('postInterceptor');
    }]);
