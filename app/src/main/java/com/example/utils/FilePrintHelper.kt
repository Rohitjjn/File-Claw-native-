package com.example.utils

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.File

/**
 * Native Print and Export helper using Android's PrintManager & PrintDocumentAdapter.
 * Works 100% offline to print or save documents as PDF.
 */
object FilePrintHelper {

    fun printWebView(
        context: Context,
        webView: WebView,
        jobName: String = "FileClaw_Document"
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val printAdapter = webView.createPrintDocumentAdapter(jobName)
        printManager.print(
            jobName,
            printAdapter,
            PrintAttributes.Builder()
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .build()
        )
    }

    fun printTextOrMarkdown(
        context: Context,
        content: String,
        title: String
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val tempWebView = WebView(context)
        val escaped = content
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                        font-size: 13px;
                        line-height: 1.6;
                        padding: 24px;
                        color: #1a1a1a;
                    }
                    h2 {
                        margin-top: 0;
                        color: #111;
                        font-size: 18px;
                    }
                    hr {
                        border: none;
                        border-top: 1px solid #ddd;
                        margin: 12px 0 20px 0;
                    }
                    pre {
                        white-space: pre-wrap;
                        word-wrap: break-word;
                        font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
                        font-size: 11px;
                        background: #f8f8f8;
                        padding: 12px;
                        border-radius: 6px;
                    }
                </style>
            </head>
            <body>
                <h2>$title</h2>
                <hr>
                <pre>$escaped</pre>
            </body>
            </html>
        """.trimIndent()

        tempWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printAdapter = tempWebView.createPrintDocumentAdapter(title)
                printManager.print(
                    title,
                    printAdapter,
                    PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                        .build()
                )
            }
        }
        tempWebView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    fun printImage(
        context: Context,
        imageFile: File,
        title: String
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val tempWebView = WebView(context)
        val imageUri = android.net.Uri.fromFile(imageFile).toString()
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <style>
                    body {
                        margin: 0;
                        padding: 16px;
                        text-align: center;
                        font-family: sans-serif;
                    }
                    h3 {
                        margin-top: 0;
                        color: #333;
                    }
                    img {
                        max-width: 100%;
                        max-height: 90vh;
                        height: auto;
                        object-fit: contain;
                        display: block;
                        margin: 0 auto;
                    }
                </style>
            </head>
            <body>
                <h3>$title</h3>
                <img src="$imageUri" />
            </body>
            </html>
        """.trimIndent()

        tempWebView.settings.apply {
            allowFileAccess = true
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = true
        }

        tempWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printAdapter = tempWebView.createPrintDocumentAdapter(title)
                printManager.print(
                    title,
                    printAdapter,
                    PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                        .build()
                )
            }
        }
        tempWebView.loadDataWithBaseURL("file:///", html, "text/html", "UTF-8", null)
    }
}
