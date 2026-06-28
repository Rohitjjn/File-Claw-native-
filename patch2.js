const fs = require('fs');

const filePath = 'app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt';
let content = fs.readFileSync(filePath, 'utf-8');

// Replace the fully qualified extension functions that break receiver resolution
content = content.replace(/androidx\.compose\.foundation\.gestures\.detectTapGestures/g, 'detectTapGestures');
content = content.replace(/androidx\.compose\.foundation\.gestures\.awaitEachGesture/g, 'awaitEachGesture');

fs.writeFileSync(filePath, content, 'utf-8');
console.log('Fixed gesture imports');
