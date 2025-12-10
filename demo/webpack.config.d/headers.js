// This file is automatically picked up by the Kotlin Gradle plugin.
// It configures the webpack-dev-server to add the required headers
// to enable SharedArrayBuffer.
if (config.devServer) {
    config.devServer.headers = {
        ...config.devServer.headers,
        "Cross-Origin-Opener-Policy": "same-origin",
        "Cross-Origin-Resource-Policy": "same-site",
        "Cross-Origin-Embedder-Policy": "require-corp",
    };
}
