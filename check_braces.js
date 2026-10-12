const fs = require('fs');
const content = fs.readFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', 'utf8');
let open = 0;
let lastOpenLine = 0;
const lines = content.split('\n');
for(let i = 0; i < lines.length; i++) {
    for (let j = 0; j < lines[i].length; j++) {
        if (lines[i][j] === '{') { open++; lastOpenLine = i+1; }
        if (lines[i][j] === '}') { open--; }
    }
}
console.log("Open braces left: " + open + " (last open on line " + lastOpenLine + ")");
