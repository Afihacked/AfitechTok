package com.afitech.sosmedtoolkit.ui.adapters

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.format.DateFormat
import android.util.Size
import android.view.*
import android.widget.*
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.afitech.sosmedtoolkit.R
import com.afitech.sosmedtoolkit.data.model.DownloadHistory
import com.afitech.sosmedtoolkit.ui.helpers.DownloadHistoryDiffCallback
import com.bumptech.glide.Glide
import java.io.File

class HistoryAdapter(
    private val context: Context,
    private var historyList: List<DownloadHistory>,
    private val onDelete: (DownloadHistory) -> Unit,
    private val onMultipleDelete: (List<DownloadHistory>) -> Unit,
    private val onSelectionChanged: (() -> Unit)? = null
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    private val selectedItems = mutableSetOf<DownloadHistory>()
    private var isSelectionMode = false

    class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumbnail: ImageView = view.findViewById(R.id.imageThumbnail)
        val fileName: TextView = view.findViewById(R.id.textFileName)
        val fileType: TextView = view.findViewById(R.id.textFileType)
        val fileSize: TextView = view.findViewById(R.id.textFileSize)
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
        holder.fileSize.text = getFileSizeReadable(uri)

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
                    if (bitmap != null) holder.thumbnail.setImageBitmap(bitmap)
                    else holder.thumbnail.setImageResource(R.drawable.ic_file)
                }
                "Audio" -> holder.thumbnail.setImageResource(R.drawable.ic_music_note)
                "Image" -> Glide.with(context).load(uri).placeholder(R.drawable.ic_placeholder).into(holder.thumbnail)
                else -> holder.thumbnail.setImageResource(R.drawable.ic_file)
            }
        } else {
            holder.thumbnail.setImageResource(R.drawable.ic_broken_image)
        }

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
            toggleSelection(history)
            true
        }

        holder.btnMore.setOnClickListener {
            val popup = PopupMenu(context, holder.btnMore)
            popup.menu.add("Rincian")
            popup.menu.add("Bagikan")
            popup.menu.add("Hapus")
            popup.setOnMenuItemClickListener { item ->
                when (item.title) {
                    "Rincian" -> {
                        showDetailDialog(history)
                        true
                    }
                    "Bagikan" -> {
                        if (fileExists(uri)) {
                            shareFile(history, uri)
                        } else {
                            Toast.makeText(context, "File tidak ditemukan", Toast.LENGTH_SHORT).show()
                        }
                        true
                    }
                    "Hapus" -> {
                        // Langsung hapus, JANGAN onDelete di sini.
                        showDeleteConfirmation(
                            title = "Konfirmasi Hapus",
                            message = "Yakin ingin menghapus file ini?",
                            onConfirm = {
                                // PANGGIL CALLBACK, bukan lagi show dialog di Fragment
                                onDelete(history)
                            }
                        )
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

    }

    private fun showDeleteConfirmation(
        title: String,
        message: String,
        onConfirm: () -> Unit
    ) {
        val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Hapus") { _, _ -> onConfirm() }
            .setNegativeButton("Batal", null)
            .create()

        dialog.setOnShowListener {
            val color = ContextCompat.getColor(context, R.color.colorPrimary)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(color)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(color)
        }

        dialog.show()
    }


    fun deleteSelectedItems() {
        val toDelete = selectedItems.toList()
        if (toDelete.isNotEmpty()) {
            showDeleteConfirmation(
                title = "Konfirmasi Hapus Beberapa File",
                message = "Yakin ingin menghapus ${toDelete.size} file terpilih?",
                onConfirm = {
                    clearSelection()
                    onMultipleDelete(toDelete)
                }
            )
        }
    }
    private fun showDetailDialog(history: DownloadHistory) {
        val uri = Uri.parse(history.filePath)
        val fileSizeReadable = getFileSizeReadable(uri)
        val dateFormatted = DateFormat.format("dd MMM yyyy, HH:mm", history.downloadDate).toString()

        val displayPath = when {
            history.fileType.equals("Video", ignoreCase = true) -> "/storage/emulated/0/Movies/Afitech-Tiktok/${history.fileName}"
            history.fileType.equals("Audio", ignoreCase = true) -> "/storage/emulated/0/Music/Afitech-Tiktok/${history.fileName}"
            history.fileType.equals("Image", ignoreCase = true) -> "/storage/emulated/0/Pictures/Afitech-Tiktok/${history.fileName}"
            else -> "/storage/emulated/0/Download/TikTokDownloads/${history.fileName}"
        }

        val view = LayoutInflater.from(context).inflate(R.layout.rincian_file_dialog, null)

        view.findViewById<TextView>(R.id.textNama).text = history.fileName
        view.findViewById<TextView>(R.id.textTanggal).text = dateFormatted
        view.findViewById<TextView>(R.id.textLokasi).text = displayPath
        view.findViewById<TextView>(R.id.textUkuran).text = fileSizeReadable

        val dialog = AlertDialog.Builder(context)
            .setTitle("Rincian File")
            .setView(view)
            .setNegativeButton("Tutup", null)
            .setPositiveButton("Salin Lokasi") { _, _ -> }
            .create()

        dialog.setOnShowListener {
            val primaryColor = ContextCompat.getColor(context, R.color.colorPrimary)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(primaryColor)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(primaryColor)

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("filePath", displayPath)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Lokasi berhasil disalin", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun getFileSizeReadable(fileUri: Uri): String {
        return try {
            if (fileUri.scheme == "file") {
                val file = File(fileUri.path ?: "")
                return android.text.format.Formatter.formatShortFileSize(context, file.length())
            }
            val cursor = context.contentResolver.query(fileUri, null, null, null, null)
            cursor?.use {
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (cursor.moveToFirst() && sizeIndex != -1) {
                    val size = cursor.getLong(sizeIndex)
                    if (size > 0L) {
                        return android.text.format.Formatter.formatShortFileSize(context, size)
                    }
                }
            }
            context.contentResolver.openAssetFileDescriptor(fileUri, "r")?.use { afd ->
                val size = afd.length
                if (size >= 0) {
                    return android.text.format.Formatter.formatShortFileSize(context, size)
                }
            }
            "0 B"
        } catch (e: Exception) {
            "0 B"
        }
    }

    private fun fileExists(uri: Uri): Boolean {
        return try {
            val existsViaContent = context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
            val path = uri.path
            val fileExistsViaPath = path?.let { File(it).exists() } ?: false
            existsViaContent || fileExistsViaPath
        } catch (e: Exception) {
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
        val diffCallback = DownloadHistoryDiffCallback(historyList, newList)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        historyList = newList
        diffResult.dispatchUpdatesTo(this)
        clearSelection()
    }

    private fun toggleSelection(item: DownloadHistory) {
        if (selectedItems.contains(item)) {
            selectedItems.remove(item)
            if (selectedItems.isEmpty()) isSelectionMode = false
        } else {
            selectedItems.add(item)
            isSelectionMode = true
        }
        notifyDataSetChanged()
        onSelectionChanged?.invoke()
    }

    fun selectItem(item: DownloadHistory) {
        if (!selectedItems.contains(item)) {
            selectedItems.add(item)
        }
        isSelectionMode = true
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
