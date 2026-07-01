const fs = require('fs');
let fileContent = fs.readFileSync('app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', 'utf8');

const targetStr = `        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes">
            <script src="file:///android_asset/mammoth/mammoth.browser.js"></script>`;

const replaceStr = `        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes">
            <!-- Add jszip and docx-preview -->
            <script src="https://unpkg.com/jszip/dist/jszip.min.js"></script>
            <script src="https://unpkg.com/docx-preview/dist/docx-preview.min.js"></script>`;

fileContent = fileContent.replace(targetStr, replaceStr);

const scriptTargetStr = `            <script>
                function base64ToArrayBuffer(base64) {
                    var binary_string = window.atob(base64);
                    var len = binary_string.length;
                    var bytes = new Uint8Array(len);
                    for (var i = 0; i < len; i++) {
                        bytes[i] = binary_string.charCodeAt(i);
                    }
                    return bytes.buffer;
                }

                var totalPages = 1;

                window.scrollToPage = function(pageNum) {
                    var height = document.documentElement.scrollHeight || document.body.scrollHeight;
                    var targetScroll = (height - window.innerHeight) * ((pageNum - 1) / totalPages);
                    window.scrollTo({top: targetScroll, behavior: 'smooth'});
                };

                window.addEventListener('scroll', function() {
                    var scrollTop = window.scrollY;
                    var docHeight = (document.documentElement.scrollHeight || document.body.scrollHeight) - window.innerHeight;
                    if (docHeight <= 0) return;
                    var pct = scrollTop / docHeight;
                    var currentPage = Math.min(totalPages, Math.max(1, Math.round(pct * (totalPages - 1)) + 1));
                    if (window.Android && window.Android.onPageChanged) {
                        window.Android.onPageChanged(currentPage, totalPages);
                    }
                });

                var base64Data = "${base64Docx}";
                var arrayBuffer = base64ToArrayBuffer(base64Data);

                mammoth.convertToHtml({arrayBuffer: arrayBuffer})
                    .then(function(result){
                        document.getElementById("output").innerHTML = result.value;
                        
                        var approxHeight = document.documentElement.scrollHeight || document.body.scrollHeight;
                        totalPages = Math.max(1, Math.ceil(approxHeight / window.innerHeight));
                        if (window.Android && window.Android.onPageChanged) {
                            window.Android.onPageChanged(1, totalPages);
                        }
                    })
                    .done();
            </script>`;

const scriptReplaceStr = `            <script>
                function base64ToArrayBuffer(base64) {
                    var binary_string = window.atob(base64);
                    var len = binary_string.length;
                    var bytes = new Uint8Array(len);
                    for (var i = 0; i < len; i++) {
                        bytes[i] = binary_string.charCodeAt(i);
                    }
                    return bytes.buffer;
                }

                var totalPages = 1;

                window.scrollToPage = function(pageNum) {
                    var height = document.documentElement.scrollHeight || document.body.scrollHeight;
                    var targetScroll = (height - window.innerHeight) * ((pageNum - 1) / totalPages);
                    window.scrollTo({top: targetScroll, behavior: 'smooth'});
                };

                window.addEventListener('scroll', function() {
                    var scrollTop = window.scrollY;
                    var docHeight = (document.documentElement.scrollHeight || document.body.scrollHeight) - window.innerHeight;
                    if (docHeight <= 0) return;
                    var pct = scrollTop / docHeight;
                    var currentPage = Math.min(totalPages, Math.max(1, Math.round(pct * (totalPages - 1)) + 1));
                    if (window.Android && window.Android.onPageChanged) {
                        window.Android.onPageChanged(currentPage, totalPages);
                    }
                });

                var base64Data = "${base64Docx}";
                var arrayBuffer = base64ToArrayBuffer(base64Data);
                
                var options = {
                    className: "docx",
                    inWrapper: false,
                    ignoreWidth: false,
                    ignoreHeight: false,
                    ignoreFonts: "$isNightMode" === "true", // ignore fonts for night mode
                    breakPages: true,
                    ignoreLastRenderedPageBreak: false,
                    experimental: true,
                    trimXmlDeclaration: true,
                    debug: false
                };
                
                // Add a wrapper to fix black background for wrapper in docx-preview if not in night mode
                var container = document.getElementById("output");
                if ("$isNightMode" === "true") {
                    container.style.color = "#f5f5f3";
                }
                
                docx.renderAsync(arrayBuffer, container, null, options).then(function() {
                    var approxHeight = document.documentElement.scrollHeight || document.body.scrollHeight;
                    totalPages = Math.max(1, Math.ceil(approxHeight / window.innerHeight));
                    if (window.Android && window.Android.onPageChanged) {
                        window.Android.onPageChanged(1, totalPages);
                    }
                });
            </script>`;

fileContent = fileContent.replace(scriptTargetStr, scriptReplaceStr);
fs.writeFileSync('app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', fileContent);
console.log("Updated DocxPreview");
