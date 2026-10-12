const fs = require('fs');
const content = fs.readFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', 'utf8');
const stack = [];
const lines = content.split('\n');
for(let i = 0; i < lines.length; i++) {
    let line = lines[i];
    for (let j = 0; j < line.length; j++) {
        if (line[j] === '{') stack.push(i+1);
        if (line[j] === '}') stack.pop();
    }
}
console.log("Unclosed brace at line: " + stack);
