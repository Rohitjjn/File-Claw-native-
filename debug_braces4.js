const fs = require('fs');
const content = fs.readFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', 'utf8');
let open = 0;
const lines = content.split('\n');
for(let i = 550; i < 575; i++) {
    let line = lines[i];
    for (let j = 0; j < line.length; j++) {
        if (line[j] === '{') open++;
        if (line[j] === '}') open--;
    }
    console.log((i+1) + ": " + open + " | " + line);
}
