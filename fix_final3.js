const fs = require('fs');
let content = fs.readFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', 'utf8');

if (!content.includes('import androidx.compose.runtime.collectAsState')) {
    content = content.replace('import androidx.compose.runtime.Composable', 'import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.collectAsState');
}

content = content.replace('viewModel.currentFileState.androidx.compose.runtime.collectAsState()', 'viewModel.currentFileState.collectAsState()');
content = content.replace(/onBackClick = onBack/g, 'onBackClick = onBackClick');

fs.writeFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', content);
