const fs = require('fs');
let content = fs.readFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', 'utf8');

content = content.replace(/IconButton\(onClick = onBack\)/g, 'IconButton(onClick = onBackClick)');
content = content.replace(/onBackClick = onBackClickClick/g, 'onBackClick = onBackClick');

fs.writeFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', content);
