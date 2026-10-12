const fs = require('fs');
let content = fs.readFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', 'utf8');

const assetLoaderCode = `val assetLoader = androidx.webkit.WebViewAssetLoader.Builder()
                        .addPathHandler("/assets/", androidx.webkit.WebViewAssetLoader.AssetsPathHandler(context))
                        .build()
                    webViewClient = object : android.webkit.WebViewClient() {`;

content = content.replace(/webViewClient = object : android\.webkit\.WebViewClient\(\) \{/g, assetLoaderCode);
fs.writeFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', content);
