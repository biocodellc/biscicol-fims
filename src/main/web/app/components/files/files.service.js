(function() {
    'use strict';

    angular.module('fims.files')
        .service('FileService', FileService);

    FileService.$inject = ['$window', 'AuthFactory'];

    function FileService($window, AuthFactory) {

        var service = {
            download: download
        };

        return service;

        function download(urlString) {
            var access_token = AuthFactory.getAccessToken();
            var url = new URL(urlString);

            var parser = document.createElement('a');
            parser.href = url;

            if (access_token) {
                if (parser.search) {
                    url += '&access_token=' + access_token;
                } else {
                    url += '?access_token=' + access_token;
                }
            }

            $window.open(url, "_self");
        }

    }
})();