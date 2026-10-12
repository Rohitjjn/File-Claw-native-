const fs = require('fs');
let content = fs.readFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', 'utf8');

// The new signature needs to match MainActivity's expectations
const oldSig = `fun FilePreviewScreen(
    fileEntity: RecentFileEntity,
    mainViewModel: MainViewModel,
    onBack: () -> Unit
)`;

const newSig = `fun FilePreviewScreen(
    fileEntity: RecentFileEntity,
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    onNavigateToEditor: (String) -> Unit = {}
)`;

content = content.replace(oldSig, newSig);
content = content.replace(/mainViewModel/g, 'viewModel');
content = content.replace(/onBack\(\)/g, 'onBackClick()');
content = content.replace(/onBackClick = onBack/g, 'onBackClick = onBackClick');

fs.writeFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', content);
