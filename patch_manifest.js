const fs = require('fs');
let content = fs.readFileSync('/app/applet/app/src/main/AndroidManifest.xml', 'utf8');

// Remove `<data android:mimeType="*/*" />`
content = content.replace(/<data android:mimeType="\*\/\*" \/>/g, '');

// Remove `<data android:host="*" />`
content = content.replace(/<data android:host="\*" \/>/g, '');

fs.writeFileSync('/app/applet/app/src/main/AndroidManifest.xml', content);
