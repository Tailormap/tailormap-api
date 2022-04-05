window.onload = function() {
  window.ui = SwaggerUIBundle({
    url: "./tailormap-api.yaml",
    dom_id: '#swagger-ui',
    deepLinking: true,
    presets: [
      SwaggerUIBundle.presets.apis,
      SwaggerUIStandalonePreset
    ],
    plugins: [
      SwaggerUIBundle.plugins.DownloadUrl
    ],
    layout: "StandaloneLayout",
    requestInterceptor: (request) => {
      const match = document.cookie.match(/XSRF-TOKEN=([0-9a-f-]+)/);
      if(match) {
        request.headers['X-XSRF-TOKEN'] = match[1];
      }
      return request;
    }
  });
};
