const fs = require('fs');

const filePath = 'app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt';
let content = fs.readFileSync(filePath, 'utf-8');

// Fix the imports I accidentally broke
content = content.replace('import detectTapGestures', 'import androidx.compose.foundation.gestures.detectTapGestures');
content = content.replace('import awaitEachGesture', 'import androidx.compose.foundation.gestures.awaitEachGesture');

fs.writeFileSync(filePath, content, 'utf-8');
console.log('Fixed imports');
