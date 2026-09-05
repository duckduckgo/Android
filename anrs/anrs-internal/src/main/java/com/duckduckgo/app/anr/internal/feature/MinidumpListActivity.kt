/*
 * Copyright (c) 2026 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.anr.internal.feature

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.android_crashkit.Crashpad
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.anr.internal.R
import com.duckduckgo.app.anr.internal.databinding.ActivityMinidumpListBinding
import com.duckduckgo.app.anr.internal.databinding.ItemMinidumpBinding
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@InjectWith(ActivityScope::class)
class MinidumpListActivity : DuckDuckGoActivity() {

    private val binding: ActivityMinidumpListBinding by viewBinding()
    private lateinit var adapter: MinidumpAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.toolbar)

        adapter = MinidumpAdapter(
            items = loadMinidumps().toMutableList(),
            onShare = { shareMinidump(it.file) },
            onDelete = { entry -> deleteMinidump(entry) },
        )
        refreshList()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_minidump_list, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_upload_pending -> {
                requestUploadForPending()
                true
            }
            R.id.action_delete_all -> {
                deleteAllMinidumps()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun refreshList() {
        val empty = adapter.itemCount == 0
        binding.emptyView.isVisible = empty
        binding.minidumpList.isVisible = !empty
        if (!empty && binding.minidumpList.adapter == null) {
            binding.minidumpList.layoutManager = LinearLayoutManager(this)
            binding.minidumpList.adapter = adapter
        }
    }

    private fun loadMinidumps(): List<MinidumpEntry> {
        val crashpadDir = filesDir.resolve("crashpad")
        val uploadedUuids = Crashpad.getUploadedReportUuids()
        return listOf("new", "pending", "completed")
            .flatMap { status ->
                crashpadDir.resolve(status)
                    .takeIf { it.isDirectory }
                    ?.listFiles { f -> f.extension == "dmp" }
                    ?.map { file ->
                        val displayStatus = if (status == "completed") {
                            if (file.nameWithoutExtension in uploadedUuids) "completed/uploaded" else "completed/skipped"
                        } else {
                            status
                        }
                        MinidumpEntry(file = file, status = displayStatus)
                    }
                    ?: emptyList()
            }
            .sortedByDescending { it.file.lastModified() }
    }

    private fun deleteMinidump(entry: MinidumpEntry) {
        entry.file.delete()
        entry.file.resolveSibling("${entry.file.nameWithoutExtension}.meta").delete()
        adapter.remove(entry)
        refreshList()
    }

    private fun deleteAllMinidumps() {
        adapter.items.toList().forEach { entry ->
            entry.file.delete()
            entry.file.resolveSibling("${entry.file.nameWithoutExtension}.meta").delete()
        }
        adapter.clear()
        refreshList()
    }

    private fun requestUploadForPending() {
        val count = Crashpad.requestUploadForPendingReports()
        android.widget.Toast.makeText(
            this,
            "Requested upload for $count report(s). Reopen this screen to see status.",
            android.widget.Toast.LENGTH_LONG,
        ).show()
    }

    private fun shareMinidump(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.crashkit.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share minidump"))
    }
}

data class MinidumpEntry(val file: File, val status: String)

class MinidumpAdapter(
    val items: MutableList<MinidumpEntry>,
    private val onShare: (MinidumpEntry) -> Unit,
    private val onDelete: (MinidumpEntry) -> Unit,
) : RecyclerView.Adapter<MinidumpAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    class ViewHolder(val binding: ItemMinidumpBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMinidumpBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = items[position]
        val sizeKb = entry.file.length() / 1024
        val date = dateFormat.format(Date(entry.file.lastModified()))
        with(holder.binding.minidumpItem) {
            setPrimaryText("${entry.file.nameWithoutExtension.takeLast(12)}  [${entry.status}]")
            setSecondaryText("$sizeKb KB · $date")
            setTrailingIconResource(com.duckduckgo.mobile.android.R.drawable.ic_trash_24)
            showTrailingIcon()
            setTrailingIconClickListener { onDelete(entry) }
            setOnClickListener { onShare(entry) }
        }
    }

    override fun getItemCount() = items.size

    fun remove(entry: MinidumpEntry) {
        val idx = items.indexOf(entry)
        if (idx >= 0) {
            items.removeAt(idx)
            notifyItemRemoved(idx)
        }
    }

    fun clear() {
        val size = items.size
        items.clear()
        notifyItemRangeRemoved(0, size)
    }
}
