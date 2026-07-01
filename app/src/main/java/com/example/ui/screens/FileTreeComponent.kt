package com.example.ui.screens

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import java.io.File
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class FileNode(val file: File, val depth: Int, val isExpanded: Boolean = false)

@Composable
fun FileTreeComponent(
    rootDir: File,
    onFileClick: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedPaths by rememberSaveable { mutableStateOf(setOf<String>()) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var flatNodes by remember { mutableStateOf<List<FileNode>>(emptyList()) }
    val listState = rememberLazyListState()

    LaunchedEffect(rootDir, expandedPaths, searchQuery) {
        withContext(Dispatchers.IO) {
            val nodes = mutableListOf<FileNode>()
            fun traverse(dir: File, depth: Int) {
                val files = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                files?.forEach { f ->
                    val matchesSearch = searchQuery.isEmpty() || f.name.contains(searchQuery, ignoreCase = true)
                    if (matchesSearch || (f.isDirectory && expandedPaths.contains(f.absolutePath))) {
                        if (matchesSearch) nodes.add(FileNode(f, depth, expandedPaths.contains(f.absolutePath)))
                        if (f.isDirectory && (expandedPaths.contains(f.absolutePath) || searchQuery.isNotEmpty())) {
                            traverse(f, depth + 1)
                        }
                    }
                }
            }
            
            val children = rootDir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            children?.forEach { f ->
                val matchesSearch = searchQuery.isEmpty() || f.name.contains(searchQuery, ignoreCase = true)
                if (matchesSearch) nodes.add(FileNode(f, 0, expandedPaths.contains(f.absolutePath)))
                if (f.isDirectory && (expandedPaths.contains(f.absolutePath) || searchQuery.isNotEmpty())) {
                    traverse(f, if (matchesSearch) 1 else 0)
                }
            }
            flatNodes = nodes
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = { Text("Search files...", fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(20.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(20.dp))
                    }
                }
            },
            singleLine = true,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            state = listState,
        ) {
            items(flatNodes, key = { it.file.absolutePath }) { node ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (node.file.isDirectory) {
                                expandedPaths = if (expandedPaths.contains(node.file.absolutePath)) {
                                    expandedPaths - node.file.absolutePath
                                } else {
                                    expandedPaths + node.file.absolutePath
                                }
                            } else {
                                onFileClick(node.file)
                            }
                        }
                        .padding(start = (node.depth * 16).dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (node.file.isDirectory) {
                            if (node.isExpanded || searchQuery.isNotEmpty()) Icons.Default.FolderOpen else Icons.Default.Folder
                        } else {
                            Icons.Default.InsertDriveFile
                        },
                        contentDescription = null,
                        tint = if (node.file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = node.file.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        fontWeight = if (node.file.isDirectory) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }
    }
}
