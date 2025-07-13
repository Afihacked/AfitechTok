package com.afitech.sosmedtoolkit.ui.adapters

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import android.view.*
import android.widget.*
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.afitech.sosmedtoolkit.R
import com.afitech.sosmedtoolkit.data.model.DownloadHistory
import com.bumptech.glide.Glide
import java.io.File

class HistoryAdapter(
    private val context: Context,
    private var historyList: List<DownloadHistory>,
    private val onDelete: (DownloadHistory) -> Unit,
    private val onMultipleDelete: (List<DownloadHistory>) -> Unit,
    private val onSelectionChanged: (() -> Unit)? = null    // tambahan callback opsional
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    private val selectedItems = mutableSetOf<DownloadHistory>()
    private var isSelectionMode = false

    class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumbnail: ImageView = view.findViewById(R.id.imageThumbnail)
        val fileName: TextView = view.findViewById(R.id.textFileName)
        val fileType: TextView = view.findViewById(R.id.textFileType)
        val btnMore: ImageButton = view.findViewById(R.id.btnMore)
        val infoContainer: View = view.findViewById(R.id.infoContainer)
        val rootLayout: View = view
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_download_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val history = historyList[position]
        val uri = Uri.parse(history.filePath)

        holder.fileName.text = history.fileName
        holder.fileType.text = history.fileType
        val textColor = ContextCompat.getColor(context, R.color.colorOnSurface)
        holder.fileName.setTextColor(textColor)
        holder.fileType.setTextColor(textColor)

        val fileExists = fileExists(uri)

        if (fileExists) {
            when (history.fileType) {
                "Video" -> {
                    val filePath = getRealPathFromURI(context, uri) ?: history.filePath
                    val bitmap: Bitmap? = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            context.contentResolver.loadThumbnail(uri, Size(200, 200), null)
                        } else {
                            if (filePath.startsWith("content://")) {
                                val retriever = MediaMetadataRetriever()
                                retriever.setDataSource(context, uri)
                                retriever.getFrameAtTime(1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC).also {
                                    retriever.release()
                                }
                            } else {
                                ThumbnailUtils.createVideoThumbnail(filePath, MediaStore.Images.Thumbnails.MINI_KIND)
                            }
                        }
                    } catch (e: Exception) {
                        null
                    }

                    if (bitmap != null) {
                        holder.thumbnail.setImageBitmap(bitmap)
                    } else {
                        holder.thumbnail.setImageResource(R.drawable.ic_file)
                    }
                }

                "Audio" -> holder.thumbnail.setImageResource(R.drawable.ic_music_note)

                "Image" -> Glide.with(context)
                    .load(uri)
                    .placeholder(R.drawable.ic_placeholder)
                    .into(holder.thumbnail)

                else -> holder.thumbnail.setImageResource(R.drawable.ic_file)
            }
        } else {
            holder.thumbnail.setImageResource(R.drawable.ic_broken_image)
        }

        // Highlight jika selected
        holder.rootLayout.setBackgroundColor(
            ContextCompat.getColor(
                context,
                if (selectedItems.contains(history)) R.color.selection_bg else android.R.color.transparent
            )
        )

        holder.infoContainer.setOnClickListener {
            if (isSelectionMode) {
                toggleSelection(history)
            } else if (fileExists) {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, when (history.fileType) {
                        "Video" -> "video/*"
                        "Audio" -> "audio/*"
                        "Image" -> "image/*"
                        else -> "*/*"
                    })
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "File tidak ditemukan", Toast.LENGTH_SHORT).show()
            }
        }

        holder.infoContainer.setOnLongClickListener {
            if (!isSelectionMode) {
                isSelectionMode = true
            }
            toggleSelection(history)

            if (selectedItems.size > 1) {
                val popup = PopupMenu(context, holder.btnMore)
                popup.menu.add("Hapus Dipilih")
                popup.setOnMenuItemClickListener { item ->
                    if (item.title == "Hapus Dipilih") {
                        val toDelete = selectedItems.toList()
                        clearSelection()
                        onMultipleDelete(toDelete)
                        true
                    } else false
                }
                popup.show()
            }
            true
        }

        holder.btnMore.setOnClickListener {
            val popup = PopupMenu(context, holder.btnMore)
            popup.menu.add("Bagikan")
            popup.menu.add("Hapus")
            popup.setOnMenuItemClickListener { item ->
                when (item.title) {
                    "Bagikan" -> {
                        if (fileExists(uri)) {
                            shareFile(history, uri)
                        } else {
                            Toast.makeText(context, "File tidak ditemukan", Toast.LENGTH_SHORT).show()
                        }
                        true
                    }

                    "Hapus" -> {
                        try {
                            onDelete(history)
                        } catch (e: Exception) {
                            android.util.Log.e("HistoryAdapter", "Delete error for ${history.fileName}", e)
                        }
                        true
                    }


                    else -> false
                }
            }
            popup.show()
        }

    }

    private fun fileExists(uri: Uri): Boolean {
        return try {
            // Cek pakai content resolver
            val existsViaContent = context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false

            // Jika gagal, cek lewat path fallback (jika tersedia)
            val path = uri.path
            val fileExistsViaPath = path?.let { File(it).exists() } ?: false

            existsViaContent || fileExistsViaPath
        } catch (e: Exception) {
            android.util.Log.d("HistoryAdapter", "fileExists exception: ${e.message}")
            false
        }
    }


    fun getCurrentList(): List<DownloadHistory> = historyList


    private fun shareFile(history: DownloadHistory, uri: Uri) {
        val mimeType = when (history.fileType) {
            "Video" -> "video/*"
            "Audio" -> "audio/*"
            "Image" -> "image/*"
            else -> "*/*"
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Bagikan file"))
    }

    fun isSelectionMode(): Boolean = isSelectionMode

    fun cancelSelection() {
        clearSelection()
    }

    override fun getItemCount(): Int = historyList.size

    fun updateData(newList: List<DownloadHistory>) {
        historyList = newList
        notifyDataSetChanged()
        clearSelection()
    }


    private fun toggleSelection(item: DownloadHistory) {
        if (selectedItems.contains(item)) {
            selectedItems.remove(item)
            if (selectedItems.isEmpty()) isSelectionMode = false
        } else {
            selectedItems.add(item)
        }
        notifyDataSetChanged()
        onSelectionChanged?.invoke()
    }

    fun getSelectedItems(): List<DownloadHistory> = selectedItems.toList()

    fun clearSelection() {
        selectedItems.clear()
        isSelectionMode = false
        notifyDataSetChanged()
        onSelectionChanged?.invoke()
    }

    private fun getRealPathFromURI(context: Context, contentUri: Uri): String? {
        // Hanya untuk video, jika perlu bisa ditambah untuk audio/image
        val cursor = context.contentResolver.query(contentUri, arrayOf(MediaStore.Video.Media.DATA), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                return it.getString(columnIndex)
            }
        }
        return null
    }
}
