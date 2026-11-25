package com.example.ui.library

import android.app.DownloadManager
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.BookmarkEntity
import com.example.ui.theme.DaxYellow
import com.example.viewmodel.BrowserViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    viewModel: BrowserViewModel,
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit,
) {
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var editingBookmark by remember { mutableStateOf<BookmarkEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    val filtered = remember(bookmarks, searchQuery) {
        if (searchQuery.isBlank()) bookmarks
        else bookmarks.filter {
            it.title.contains(searchQuery, ignoreCase = true) || it.url.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Bookmarks") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Navigate up")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Outlined.Add, contentDescription = "Add Bookmark")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search bookmarks") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (filtered.isEmpty()) {
                EmptyLibraryState(
                    icon = Icons.Outlined.BookmarkBorder,
                    title = "No bookmarks yet",
                    subtitle = "Save sites from the browser menu or tap + to add one.",
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(filtered, key = { it.id }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onOpenUrl(item.url)
                                    onBack()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = {
                                    viewModel.saveBookmark(
                                        id = item.id,
                                        title = item.title,
                                        url = item.url,
                                        isFavorite = !item.isFavorite,
                                    )
                                },
                            ) {
                                Icon(
                                    imageVector = if (item.isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                                    contentDescription = "Toggle Favorite",
                                    tint = if (item.isFavorite) DaxYellow else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { editingBookmark = item }) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Edit bookmark")
                            }
                            IconButton(onClick = { viewModel.deleteBookmark(item.id) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete bookmark")
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        BookmarkEditDialog(
            initial = null,
            onDismiss = { showAddDialog = false },
            onSave = { title, url, isFav ->
                viewModel.saveBookmark(title = title, url = url, isFavorite = isFav)
                showAddDialog = false
            },
        )
    }

    editingBookmark?.let { target ->
        BookmarkEditDialog(
            initial = target,
            onDismiss = { editingBookmark = null },
            onSave = { title, url, isFav ->
                viewModel.saveBookmark(id = target.id, title = title, url = url, isFavorite = isFav)
                editingBookmark = null
            },
        )
    }
}

@Composable
private fun BookmarkEditDialog(
    initial: BookmarkEntity?,
    onDismiss: () -> Unit,
    onSave: (title: String, url: String, isFavorite: Boolean) -> Unit,
) {
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var url by remember { mutableStateOf(initial?.url.orEmpty()) }
    var isFav by remember { mutableStateOf(initial?.isFavorite ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Bookmark" else "Edit Bookmark") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { isFav = !isFav },
                ) {
                    Checkbox(checked = isFav, onCheckedChange = { isFav = it })
                    Text("Pin to New Tab Favorites")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, url, isFav) }, enabled = url.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: BrowserViewModel,
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit,
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val formatter = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    BackHandler { onBack() }

    val filtered = remember(history, query) {
        if (query.isBlank()) history
        else history.filter {
            it.title.contains(query, ignoreCase = true) || it.url.contains(query, ignoreCase = true)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Navigate up")
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = viewModel::clearAllHistory) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear History")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search history") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (filtered.isEmpty()) {
                EmptyLibraryState(
                    icon = Icons.Outlined.History,
                    title = "No browsing history",
                    subtitle = "Pages you visit in regular tabs appear here until cleared with the Fire button.",
                )
            } else {
                LazyColumn {
                    items(filtered, key = { it.id }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onOpenUrl(item.url)
                                    onBack()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(formatter.format(Date(item.visitedAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { viewModel.deleteHistoryItem(item.id) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Remove entry")
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val formatter = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    BackHandler { onBack() }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Navigate up")
                    }
                },
                actions = {
                    if (downloads.isNotEmpty()) {
                        IconButton(onClick = viewModel::clearDownloads) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear Downloads")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (downloads.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                EmptyLibraryState(
                    icon = Icons.Outlined.Download,
                    title = "No downloads yet",
                    subtitle = "Files you download from websites will be listed here.",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                items(downloads, key = { it.id }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                runCatching {
                                    context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.fileName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = "${item.status} • ${formatter.format(Date(item.createdAt))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { viewModel.deleteDownload(item.id) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Remove download")
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryState(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
