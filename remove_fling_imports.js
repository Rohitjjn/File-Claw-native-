const fs = require('fs');
const path = require('path');

function walkDir(dir, callback) {
    fs.readdirSync(dir).forEach(f => {
        let dirPath = path.join(dir, f);
        let isDirectory = fs.statSync(dirPath).isDirectory();
        isDirectory ? 
            walkDir(dirPath, callback) : callback(path.join(dir, f));
    });
}

walkDir('app/src/main/java', (filePath) => {
    if (filePath.endsWith('.kt')) {
        let content = fs.readFileSync(filePath, 'utf8');
        let newContent = content.replace(/import io\.iamjosephmj\.flinger\..*\n/g, '');
        if (content !== newContent) {
            fs.writeFileSync(filePath, newContent, 'utf8');
            console.log('Updated imports ' + filePath);
        }
    }
});
