const fs = require('fs');
let content = fs.readFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', 'utf8');

const badString = `val assetLoader = androidx.webkit.WebViewAssetLoader.Builder()
                        .addPathHandler("/assets/", androidx.webkit.WebViewAssetLoader.AssetsPathHandler(context))
                        .build()
                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: android.webkit.WebView?,
                            request: android.webkit.WebResourceRequest?
                        ): android.webkit.WebResourceResponse? {
                            return assetLoader.shouldInterceptRequest(request?.url!!) ?: super.shouldInterceptRequest(view, request)
                        }`;

const goodString = `val assetLoader = androidx.webkit.WebViewAssetLoader.Builder()
                        .addPathHandler("/assets/", androidx.webkit.WebViewAssetLoader.AssetsPathHandler(context))
                        .build()
                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: android.webkit.WebView?,
                            request: android.webkit.WebResourceRequest?
                        ): android.webkit.WebResourceResponse? {
                            return assetLoader.shouldInterceptRequest(request?.url!!) ?: super.shouldInterceptRequest(view, request)
                        }`;

content = content.replace(new RegExp(badString.replace(/[.*+?^\${}()|[\]\\]/g, '\\$&'), 'g'), goodString + '\n');
fs.writeFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', content);
