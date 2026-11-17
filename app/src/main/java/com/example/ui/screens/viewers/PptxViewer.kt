package com.example.ui.screens.viewers

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.RecentFileEntity
import com.example.viewmodel.MainViewModel
import java.io.File
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

@Composable
fun PptxPreviewWebView(
    base64Pptx: String,
    fileEntity: RecentFileEntity,
    isNightMode: Boolean,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var isWebViewLoading by remember(base64Pptx) { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<android.webkit.WebView?>(null) }
    // AUD-005: honest failure state. Any main-frame error (or a 15 s stall) flips to
    // a labeled error card with the reason and a Retry that rebuilds the WebView —
    // never an eternal "Loading Presentation…" spinner.
    var loadFailed by remember(base64Pptx) { mutableStateOf<String?>(null) }
    var pptxReloadKey by remember(base64Pptx) { mutableIntStateOf(0) }

    val context = androidx.compose.ui.platform.LocalContext.current

    // 15 s load watchdog: a wedged renderer must not spin forever (AUD-005).
    LaunchedEffect(pptxReloadKey) {
        kotlinx.coroutines.delay(15_000)
        if (isWebViewLoading) {
            isWebViewLoading = false
            loadFailed = "The presentation renderer took too long to respond (15 s watchdog)."
        }
    }

    Box(modifier = modifier.fillMaxSize().background(if (isNightMode) Color(0xFF151514) else Color(0xFFfaf9f5))) {
        if (loadFailed == null) key(pptxReloadKey) {
        AndroidView(
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    webViewRef = this
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = false
                        allowContentAccess = false
                        builtInZoomControls = true
                        displayZoomControls = false
                        setSupportZoom(true)
                        useWideViewPort = true
                        loadWithOverviewMode = true
                    }
                    setBackgroundColor(if (isNightMode) 0xFF151514.toInt() else 0xFFfaf9f5.toInt())
                    
                    addJavascriptInterface(object : Any() {
                        @android.webkit.JavascriptInterface
                        fun onDocumentLoaded(totalPages: Int) {
                            post { 
                                isWebViewLoading = false
                            }
                        }
                        @android.webkit.JavascriptInterface
                        fun onDocumentError(error: String) {
                            post {
                                isWebViewLoading = false
                                loadFailed = error.take(180)
                            }
                        }
                        @android.webkit.JavascriptInterface
                        fun exitWithoutSaving() {
                            post {
                                viewModel.closeCurrentFile()
                            }
                        }
                    }, "Android")
                    
                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                            view?.evaluateJavascript("loadPptx('$base64Pptx', $isNightMode);", null)
                        }
                        override fun onReceivedError(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                            super.onReceivedError(view, request, error)
                            // Main-frame failure ⇒ honest error card (AUD-005). Sub-resource
                            // failures keep the spinner decision to the watchdog/onDocumentError.
                            if (request?.isForMainFrame == true) {
                                android.util.Log.e("WebViewPptx", "Error: ${error?.description}")
                                post {
                                    isWebViewLoading = false
                                    loadFailed = error?.description?.toString()?.take(180)
                                        ?: "The presentation preview page could not be loaded."
                                }
                            }
                        }
                        override fun onReceivedHttpError(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?, errorResponse: android.webkit.WebResourceResponse?) {
                            super.onReceivedHttpError(view, request, errorResponse)
                            if (request?.isForMainFrame == true) {
                                android.util.Log.e("WebViewPptx", "HTTP Error: ${errorResponse?.statusCode}")
                                post {
                                    isWebViewLoading = false
                                    loadFailed = "The presentation preview page failed to load (HTTP ${errorResponse?.statusCode ?: "?"})."
                                }
                            }
                        }
                    }
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                            android.util.Log.d("WebViewPptx", "${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
                            return super.onConsoleMessage(consoleMessage)
                        }
                    }
                    loadUrl("file:///android_asset/editor/pptx_editor.html")
                }
            },
            update = { webView ->
                // No-op
            },
            modifier = Modifier.fillMaxSize()
        )
        }

        if (loadFailed != null) {
            // Labeled failure state (Loop 14-style recovery): never a silent dead screen.
            Column(
                modifier = Modifier.fillMaxSize()
                    .background(if (isNightMode) Color(0xFF151514) else Color(0xFFfaf9f5))
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "This presentation couldn't be rendered",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .5f)
                ) {
                    Text(
                        text = "Reason: ${loadFailed}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "The source file was not modified. Try Retry, or open it in another app.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        loadFailed = null
                        isWebViewLoading = true
                        pptxReloadKey++
                    }) { Text("Retry") }
                    OutlinedButton(onClick = { openWithFile(context, File(fileEntity.path), fileEntity.extension) }) {
                        Text("Open with…")
                    }
                }
            }
            return@Box
        }

        if (isWebViewLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isNightMode) Color(0xFF151514) else Color(0xFFfaf9f5)),
                contentAlignment = Alignment.Center
            ) {
                com.example.ui.component.PremiumLoadingIndicator(text = "Loading Presentation...")
            }
        }
    }
}


/** Unwrap themed/Compose context wrappers to reach the host Activity for orientation requests. */
