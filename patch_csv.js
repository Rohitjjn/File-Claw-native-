const fs = require('fs');
let content = fs.readFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', 'utf8');

// Replace evaluateJavascript call
content = content.replace(
    /view\?\.evaluateJavascript\("loadData\('\$base64Data'\);", null\)/,
    'view?.evaluateJavascript("loadData();", null)'
);

// Add getSpreadsheetData to JavascriptInterface
content = content.replace(
    /fun onDataChanged\(jsonStr: String\) \{/,
    '@android.webkit.JavascriptInterface\n                        fun getSpreadsheetData(): String {\n                            return base64Data\n                        }\n\n                        @android.webkit.JavascriptInterface\n                        fun onDataChanged(jsonStr: String) {'
);

// Update loadData script in html
content = content.replace(
    /function loadData\(base64Str\) \{/,
    'function loadData() {\n                                    var base64Str = window.Android.getSpreadsheetData();'
);

fs.writeFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', content);
