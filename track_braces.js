const fs = require('fs');
const content = fs.readFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', 'utf8');
let open = 0;
const lines = content.split('\n');
for(let i = 0; i < lines.length; i++) {
    for (let j = 0; j < lines[i].length; j++) {
        if (lines[i][j] === '{') open++;
        if (lines[i][j] === '}') open--;
    }
    if (lines[i].includes('fun ') && open === 0) {
        console.log("Function at line " + (i+1) + ", open = 0");
    } else if (lines[i].includes('fun ')) {
        console.log("Function at line " + (i+1) + " started with open = " + open);
    }
}
