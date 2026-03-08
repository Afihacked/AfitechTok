package com.afitech.afitechtok.ui.adapters

import android.app.AlertDialog
import android.app.Dialog
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
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.afitech.afitechtok.R
import com.afitech.afitechtok.data.model.DownloadHistory
import com.afitech.afitechtok.ui.helpers.DownloadHistoryDiffCallback
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import java.io.File

class HistoryAdapter(
    private val context: Context,
    private var historyList: List<DownloadHistory>,
    private val onMultipleDelete: (List<DownloadHistory>) -> Unit,
    private val onDelete: (DownloadHistory) -> Unit,
    private val onSelectionChanged: (() -> Unit)? = null
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {
    init {
        setHasStableIds(true)
    }
    private val selectedItems = mutableSetOf<Long>()
    private var isSelectionMode = false

    class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumbnail: ImageView = view.findViewById(R.id.imageThumbnail)
        val fileName: TextView = view.findViewById(R.id.textFileName)
        val fileType: TextView = view.findViewById(R.id.textFileType)
        val fileSize: TextView = view.findViewById(R.id.textFileSize)
        val btnMore: ImageButton = view.findViewById(R.id.btnMore)

        val rootLayout: View = view

        val iconSelected: ImageView = view.findViewById(R.id.iconSelected)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_download_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val history = historyList[position]
        val uri = Uri.parse(history.savedUri)

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
                    Glide.with(context)
                        .asBitmap()
                        .load(uri)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .placeholder(R.drawable.ic_file)
                        .into(holder.thumbnail)
                }
                "Audio" -> holder.thumbnail.setImageResource(R.drawable.ic_music_note)
                "Image" -> Glide.with(context)
                    .load(uri)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.ic_placeholder)
                    .into(holder.thumbnail)
                else -> holder.thumbnail.setImageResource(R.drawable.ic_file)
            }
        } else {
            holder.thumbnail.setImageResource(R.drawable.ic_broken_image)
        }

        holder.rootLayout.isActivated = selectedItems.contains(history.id)
        holder.iconSelected.visibility =
            if (selectedItems.contains(history.id)) View.VISIBLE else View.GONE


        holder.rootLayout.setOnClickListener {
            if (isSelectionMode) {

                holder.rootLayout.performHapticFeedback(
                    android.view.HapticFeedbackConstants.KEYBOARD_TAP
                )

                toggleSelection(history)
            } else if (fileExists) {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(
                        uri,
                        when (history.fileType) {
                            "Video" -> "video/*"
                            "Audio" -> "audio/*"
                            "Image" -> "image/*"
                            else -> "*/*"
                        }
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "File tidak ditemukan", Toast.LENGTH_SHORT).show()
            }
        }

        holder.rootLayout.setOnLongClickListener {

            holder.rootLayout.performHapticFeedback(
                android.view.HapticFeedbackConstants.LONG_PRESS
            )

            toggleSelection(history)
            true
        }

        holder.btnMore.setOnClickListener {
            showActionMenu(holder.btnMore, history)
        }
    }
    private fun showActionMenu(anchor: View, history: DownloadHistory) {

        val view = LayoutInflater.from(context)
            .inflate(R.layout.menu_history_actions, null)

        val popup = PopupWindow(
            view,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 12f
            isOutsideTouchable = true
            setBackgroundDrawable(null)
        }

        // ⬇️ ukur popup dulu
        view.measure(
            View.MeasureSpec.UNSPECIFIED,
            View.MeasureSpec.UNSPECIFIED
        )
        val popupHeight = view.measuredHeight

        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val anchorY = location[1]
        val anchorHeight = anchor.height

        val screenHeight = context.resources.displayMetrics.heightPixels

        val spaceBelow = screenHeight - (anchorY + anchorHeight)
        val spaceAbove = anchorY

        if (spaceBelow < popupHeight && spaceAbove > popupHeight) {
            // tampilkan di ATAS anchor
            popup.showAsDropDown(anchor, -view.measuredWidth + anchor.width, -anchorHeight - popupHeight)
        } else {
            // tampilkan di BAWAH anchor (default)
            popup.showAsDropDown(anchor, -view.measuredWidth + anchor.width, 0)
        }

        view.findViewById<View>(R.id.actionDetail).setOnClickListener {
            popup.dismiss()
            showDetailDialog(history)
        }

        view.findViewById<View>(R.id.actionShare).setOnClickListener {
            popup.dismiss()
            shareFile(history, Uri.parse(history.savedUri))
        }

        view.findViewById<View>(R.id.actionDelete).setOnClickListener {
            popup.dismiss()
            showDeleteConfirmDialog("Yakin ingin menghapus file ini?") {
                onDelete(history)
            }
        }
    }
    override fun getItemId(position: Int): Long {
        return historyList[position].id
    }
    private fun showDeleteConfirmDialog(
        message: String,
        onConfirm: () -> Unit
    ) {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.dialog_confirm_delete, null)

        val dialog = Dialog(context)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()

        view.findViewById<TextView>(R.id.textMessage).text = message

        view.findViewById<View>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        view.findViewById<View>(R.id.btnDelete).setOnClickListener {
            dialog.dismiss()
            onConfirm()
        }
    }

    private fun showDetailDialog(history: DownloadHistory) {

        val uri = Uri.parse(history.savedUri)
        val fileSizeReadable = getFileSizeReadable(uri)

        val mediaDuration =
            if (history.fileType.equals("Video", true) ||
                history.fileType.equals("Audio", true)
            ) {
                getVideoDuration(context, uri)
            } else null

        val dateFormatted =
            DateFormat.format("dd MMM yyyy, HH:mm", history.downloadDate).toString()

        val displayPath = when {
            history.fileType.equals("Video", true) ->
                "/storage/emulated/0/Movies/Afitech-${history.source.replaceFirstChar { it.uppercaseChar() }}/${history.fileName}"

            history.fileType.equals("Audio", true) ->
                "/storage/emulated/0/Music/Afitech-${history.source.replaceFirstChar { it.uppercaseChar() }}/${history.fileName}"

            history.fileType.equals("Image", true) ->
                "/storage/emulated/0/Pictures/Afitech-${history.source.replaceFirstChar { it.uppercaseChar() }}/${history.fileName}"

            else ->
                "/storage/emulated/0/Download/${history.source.capitalize()}Downloads/${history.fileName}"
        }

        val view = LayoutInflater.from(context)
            .inflate(R.layout.rincian_file_dialog, null)

        view.findViewById<TextView>(R.id.textNama).text = history.fileName
        view.findViewById<TextView>(R.id.textTanggal).text = dateFormatted
        view.findViewById<TextView>(R.id.textLokasi).text = displayPath
        view.findViewById<TextView>(R.id.textUkuran).text = fileSizeReadable
        view.findViewById<TextView>(R.id.textTipe).text = history.fileType

        val durationLayout = view.findViewById<View>(R.id.layoutDurasi)
        val durationText = view.findViewById<TextView>(R.id.textDurasi)

        if (mediaDuration != null) {
            durationLayout.visibility = View.VISIBLE
            durationText.text = mediaDuration
        } else {
            durationLayout.visibility = View.GONE
        }

        // ✅ CUSTOM DIALOG (FULL CONTROL)
        val dialog = Dialog(context)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()

        val btnTutup = view.findViewById<TextView>(R.id.btnTutup)
        val btnSalin = view.findViewById<TextView>(R.id.btnSalin)

        btnTutup.setOnClickListener {
            dialog.dismiss()
        }

        btnSalin.setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("filePath", displayPath)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Lokasi berhasil disalin", Toast.LENGTH_SHORT).show()
        }
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
        } catch (_: Exception) {
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

            // ⭐ penting → override nama file
            clipData = ClipData.newRawUri(history.fileName, uri)

            putExtra(Intent.EXTRA_TITLE, history.fileName)
            putExtra(Intent.EXTRA_SUBJECT, history.fileName)

            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            Intent.createChooser(shareIntent, history.fileName)
        )
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

        selectedItems.retainAll(newList.map { it.id }.toSet())

        if (selectedItems.isEmpty()) {
            isSelectionMode = false
        }

        onSelectionChanged?.invoke()
    }

    private fun toggleSelection(item: DownloadHistory) {

        val position = historyList.indexOfFirst { it.id == item.id }
        if (position == -1) return

        if (selectedItems.contains(item.id)) {
            selectedItems.remove(item.id)

            if (selectedItems.isEmpty()) {
                isSelectionMode = false
            }

        } else {
            selectedItems.add(item.id)
            isSelectionMode = true
        }

        notifyItemChanged(position)

        onSelectionChanged?.invoke()
    }

    fun selectItem(item: DownloadHistory, notify: Boolean = true) {
        selectedItems.add(item.id)
        isSelectionMode = true
        if (notify) notifyDataSetChanged()
    }
    fun refreshVisible(recyclerView: RecyclerView) {
        val layoutManager = recyclerView.layoutManager ?: return
        val first = (layoutManager as androidx.recyclerview.widget.LinearLayoutManager)
            .findFirstVisibleItemPosition()
        val last = layoutManager.findLastVisibleItemPosition()

        for (i in first..last) {
            notifyItemChanged(i)
        }
    }
    fun selectAll(items: List<DownloadHistory>) {
        selectedItems.clear()
        selectedItems.addAll(items.map { it.id })
        isSelectionMode = true
        notifyDataSetChanged()
        onSelectionChanged?.invoke()
    }

    fun getSelectedItems(): List<DownloadHistory> {
        return historyList.filter { selectedItems.contains(it.id) }
    }

    fun clearSelection() {
        selectedItems.clear()
        isSelectionMode = false
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
    private fun getVideoDuration(context: Context, uri: Uri): String? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val durationMs =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()
            retriever.release()

            durationMs?.let {
                val seconds = it / 1000
                val minutes = seconds / 60
                val remainingSeconds = seconds % 60
                String.format("%02d:%02d", minutes, remainingSeconds)
            }
        } catch (_: Exception) {
            null
        }
    }
}
