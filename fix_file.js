const fs = require('fs');
let content = fs.readFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', 'utf8');

// fix modifiers
content = content.replace(/\.androidx\.compose\.foundation\.clickable/g, '.clickable');
content = content.replace(/\.androidx\.compose\.foundation\.verticalScroll/g, '.verticalScroll');

// check imports
if (!content.includes('import androidx.compose.foundation.clickable')) {
    content = content.replace('import androidx.compose.foundation.layout.*', 'import androidx.compose.foundation.layout.*\nimport androidx.compose.foundation.clickable\nimport androidx.compose.foundation.verticalScroll');
}

fs.writeFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', content);
