package com.afitech.tikdownloader.ui.adapters

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.afitech.tikdownloader.R
import com.afitech.tikdownloader.data.model.DownloadHistory
import com.bumptech.glide.Glide

class HistoryAdapter(
    private val context: Context,
    private var historyList: List<DownloadHistory>,
    private val onDelete: (DownloadHistory) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumbnail: ImageView = view.findViewById(R.id.imageThumbnail)
        val fileName: TextView = view.findViewById(R.id.textFileName)
        val fileType: TextView = view.findViewById(R.id.textFileType)
        val btnPlay: ImageButton = view.findViewById(R.id.btnPlay)
        val btnShare: ImageButton = view.findViewById(R.id.btnShare)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_download_history, parent, false)
        return HistoryViewHolder(view)
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


    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val history = historyList[position]
        holder.fileName.text = history.fileName
        holder.fileType.text = history.fileType

        val textColor = ContextCompat.getColor(context, R.color.colorOnSurface)
        holder.fileName.setTextColor(textColor)
        holder.fileType.setTextColor(textColor)
        holder.btnPlay.setColorFilter(textColor)
        holder.btnShare.setColorFilter(textColor)
        holder.btnDelete.setColorFilter(textColor)

        val uri = Uri.parse(history.filePath)
        val fileExists = try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (e: Exception) {
            false
        }

        Log.d("DownloadHistory", "File Path: ${history.filePath}")

        // **Konfigurasi Thumbnail**
        if (fileExists) {
            when (history.fileType) {
                "Video" -> {
                    try {
                        val uri = Uri.parse(history.filePath)
                        val filePath = getRealPathFromURI(context, uri) ?: history.filePath // Konversi URI ke path absolut

                        val bitmap: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            // Android 10+ menggunakan loadThumbnail
                            context.contentResolver.loadThumbnail(uri, Size(200, 200), null)
                        } else {
                            if (filePath.startsWith("content://")) {
                                // Jika masih content URI, pakai MediaMetadataRetriever
                                val retriever = MediaMetadataRetriever()
                                retriever.setDataSource(context, uri)
                                retriever.getFrameAtTime(1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC).also {
                                    retriever.release()
                                }
                            } else {
                                // Jika sudah berupa path file, pakai ThumbnailUtils (Lama)
                                ThumbnailUtils.createVideoThumbnail(filePath, MediaStore.Images.Thumbnails.MINI_KIND)
                            }
                        }

                        if (bitmap != null) {
                            holder.thumbnail.setImageBitmap(bitmap)
                        } else {
                            holder.thumbnail.setImageResource(R.drawable.ic_file) // Default jika gagal
                        }
                    } catch (e: Exception) {
                        Log.e("ThumbnailError", "Gagal mendapatkan thumbnail video: ${e.message}")
                        holder.thumbnail.setImageResource(R.drawable.ic_file)
                    }
                }

                "Audio" -> {
                    holder.thumbnail.setImageResource(R.drawable.ic_music_note)
                }

                "Image" -> {
                    Glide.with(context)
                        .load(Uri.parse(history.filePath))
                        .placeholder(R.drawable.ic_placeholder)
                        .into(holder.thumbnail)
                }

                else -> {
                    holder.thumbnail.setImageResource(R.drawable.ic_file)
                }
            }
        } else {
            holder.thumbnail.setImageResource(R.drawable.ic_broken_image)
        }








        // **Fungsi Tombol**
        holder.btnPlay.setOnClickListener {
            if (fileExists) {
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

        holder.btnShare.setOnClickListener {
            if (fileExists) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = when (history.fileType) {
                        "Video" -> "video/*"
                        "Audio" -> "audio/*"
                        "Image" -> "image/*"
                        else -> "*/*"
                    }
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Bagikan file"))
            } else {
                Toast.makeText(context, "File tidak ditemukan", Toast.LENGTH_SHORT).show()
            }
        }

        holder.btnDelete.setOnClickListener {
            try {
                context.contentResolver.delete(uri, null, null)
                onDelete(history)
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal menghapus file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun getItemCount(): Int = historyList.size

    fun updateData(newList: List<DownloadHistory>) {
        historyList = newList
        notifyDataSetChanged()
    }
}
