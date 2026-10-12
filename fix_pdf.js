const fs = require('fs');
let content = fs.readFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', 'utf8');

const regex = /addJavascriptInterface\([\s\S]*?loadUrl\("https:\/\/appassets\.androidplatform\.net\/assets\/editor\/docx_editor\.html"\)/;
const replacement = `addJavascriptInterface(object : Any() {
                        @android.webkit.JavascriptInterface
                        fun onDocumentLoaded(totalPages: Int) {
                            post { 
                                isWebViewLoading = false
                                onPageChanged(1, totalPages)
                            }
                        }
                        @android.webkit.JavascriptInterface
                        fun onPageChanged(currentPage: Int, totalPages: Int) {
                            post {
                                onPageChanged(currentPage, totalPages)
                            }
                        }
                        @android.webkit.JavascriptInterface
                        fun onDocumentError(error: String) {
                            post { 
                                isWebViewLoading = false
                                android.widget.Toast.makeText(context, "Error: $error", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    }, "Android")
                    
                    val assetLoader = androidx.webkit.WebViewAssetLoader.Builder()
                        .addPathHandler("/assets/", androidx.webkit.WebViewAssetLoader.AssetsPathHandler(context))
                        .build()
                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: android.webkit.WebView?,
                            request: android.webkit.WebResourceRequest?
                        ): android.webkit.WebResourceResponse? {
                            return assetLoader.shouldInterceptRequest(request?.url!!) ?: super.shouldInterceptRequest(view, request)
                        }

                        override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                            postDelayed({ isWebViewLoading = false }, 500)
                        }
                    }
                    loadDataWithBaseURL("https://appassets.androidplatform.net/assets/", htmlContent, "text/html", "UTF-8", null)`;

content = content.replace(regex, replacement);
fs.writeFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', content);
