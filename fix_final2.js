const fs = require('fs');
let content = fs.readFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', 'utf8');

content = content.replace('import detectTransformGestures', 'import androidx.compose.foundation.gestures.detectTransformGestures');

const oldPdfBlock = `if (base64.isNotEmpty()) {
                    PdfPreviewWebView(base64, fileEntity, false)
                } else {`;
const newPdfBlock = `if (base64.isNotEmpty()) {
                    PdfPreview(
                    fileEntity = fileEntity,
                    isNightMode = false,
                    scrollToPage = null,
                    onScrollToPageHandled = {},
                    onLinkClicked = {},
                    modifier = Modifier.fillMaxSize(),
                    onBackClick = onBack
                )
                } else {`;

content = content.replace(oldPdfBlock, newPdfBlock);

fs.writeFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', content);
