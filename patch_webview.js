const fs = require('fs');
let content = fs.readFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', 'utf8');

// Replace all allowFileAccess = true with allowFileAccess = false
content = content.replace(/allowFileAccess\s*=\s*true/g, 'allowFileAccess = false');

// Now, we need to inject the AssetLoader into the WebView instances.
// We can use a regex to find all WebViewClient creations and augment them.
// But it might be easier to just do it manually for each WebViewClient since there are only 4.
// Let's rewrite the factory block for each.

// 1. General WebView (Line ~725)
// It has `webViewClient = android.webkit.WebViewClient()`
content = content.replace(/webViewClient = android\.webkit\.WebViewClient\(\)/g, `val assetLoader = androidx.webkit.WebViewAssetLoader.Builder()
                            .addPathHandler("/assets/", androidx.webkit.WebViewAssetLoader.AssetsPathHandler(context))
                            .addPathHandler("/local_file/", androidx.webkit.WebViewAssetLoader.PathHandler { path ->
                                try {
                                    val file = java.io.File(fileEntity.path)
                                    if (path == file.name) {
                                        val mimeType = java.net.URLConnection.guessContentTypeFromName(file.name) ?: "text/html"
                                        return@PathHandler android.webkit.WebResourceResponse(mimeType, "utf-8", file.inputStream())
                                    }
                                } catch (e: Exception) {}
                                null
                            })
                            .build()
                        webViewClient = object : android.webkit.WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: android.webkit.WebView?,
                                request: android.webkit.WebResourceRequest?
                            ): android.webkit.WebResourceResponse? {
                                return assetLoader.shouldInterceptRequest(request?.url!!) ?: super.shouldInterceptRequest(view, request)
                            }
                        }`);

// Update loadUrl("file://" + fileEntity.path) -> loadUrl("https://appassets.androidplatform.net/local_file/" + java.io.File(fileEntity.path).name)
content = content.replace(/webView\.loadUrl\("file:\/\/" \+ fileEntity\.path\)/g, 
    'webView.loadUrl("https://appassets.androidplatform.net/local_file/" + java.io.File(fileEntity.path).name)');

// Update loadUrl("file:///android_asset/...")
content = content.replace(/loadUrl\("file:\/\/\/android_asset\/(.*?)"\)/g, 
    'loadUrl("https://appassets.androidplatform.net/assets/$1")');
    
// Update HTML content hrefs and scripts 
// href="file:///android_asset/..." -> href="https://appassets.androidplatform.net/assets/..."
content = content.replace(/href="file:\/\/\/android_asset\/(.*?)"/g, 'href="https://appassets.androidplatform.net/assets/$1"');
content = content.replace(/src="file:\/\/\/android_asset\/(.*?)"/g, 'src="https://appassets.androidplatform.net/assets/$1"');

// For other WebViewClients (Docx, Pptx, Csv), we need to inject shouldInterceptRequest if they already have an object : WebViewClient()
content = content.replace(/webViewClient = object : android\.webkit\.WebViewClient\(\) \{/g, 
`val assetLoader = androidx.webkit.WebViewAssetLoader.Builder()
                        .addPathHandler("/assets/", androidx.webkit.WebViewAssetLoader.AssetsPathHandler(context))
                        .build()
                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: android.webkit.WebView?,
                            request: android.webkit.WebResourceRequest?
                        ): android.webkit.WebResourceResponse? {
                            return assetLoader.shouldInterceptRequest(request?.url!!) ?: super.shouldInterceptRequest(view, request)
                        }`);

fs.writeFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', content);
