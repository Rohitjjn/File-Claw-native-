const fs = require('fs');
let content = fs.readFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', 'utf8');

// 1. Add missing imports
const missingImports = `
import android.media.MediaPlayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
`;
content = content.replace('import java.util.*', 'import java.util.*\n' + missingImports);

// 2. Add ZoomableContainer at the end
const zoomableContainer = `

@Composable
fun ZoomableContainer(
    modifier: Modifier = Modifier,
    content: @Composable (scale: Float, scrollEnabled: Boolean) -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                androidx.compose.foundation.gestures.detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale > 1f) {
                        offset += pan
                    } else {
                        offset = androidx.compose.ui.geometry.Offset.Zero
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier.graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            )
        ) {
            content(scale, scale == 1f)
        }
    }
}
`;
if (!content.includes('fun ZoomableContainer')) {
    content += zoomableContainer;
}

// 3. Fix conflicting shouldInterceptRequest in WebViewPreview
// There is a duplicate method shouldInterceptRequest because I might have run the patch twice
// Let's replace the whole webViewClient = object : WebViewClient() block to ensure it's clean.
// Actually, it's easier to remove one of the duplicates if they exist
content = content.replace(/override fun shouldInterceptRequest\([\s\S]*?override fun shouldInterceptRequest/g, 'override fun shouldInterceptRequest');

fs.writeFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', content);
