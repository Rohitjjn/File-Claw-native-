const fs = require('fs');
let content = fs.readFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', 'utf8');

let count = 0;
content = content.replace(/val assetLoader = androidx\.webkit\.WebViewAssetLoader\.Builder\(\)\s*\.addPathHandler\("\/assets\/", androidx\.webkit\.WebViewAssetLoader\.AssetsPathHandler\(context\)\)\s*\.build\(\)\s*webViewClient = object : android\.webkit\.WebViewClient\(\) \{/g, function(match) {
    count++;
    return 'webViewClient = object : android.webkit.WebViewClient() {';
});
console.log("Replaced " + count + " occurrences.");
fs.writeFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', content);
