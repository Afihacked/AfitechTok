package com.afitech.afitechtok.ui.adapters

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.webkit.MimeTypeMap
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.afitech.afitechtok.R
import com.afitech.afitechtok.data.StorySaver
import com.afitech.afitechtok.data.model.StoryItem
import com.bumptech.glide.Glide
import kotlinx.coroutines.*
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class StoryAdapter(
    private var stories: List<StoryItem>,
    private val context: Context
) : RecyclerView.Adapter<StoryAdapter.StoryViewHolder>() {

    /**
     * 🔥 CACHE SEMENTARA
     * Hidup = selama adapter hidup
     * Adapter hancur → cache otomatis hilang
     */
    private val downloadedMap = mutableMapOf<String, String>()

    fun updateList(newStories: List<StoryItem>) {
        stories = newStories
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoryViewHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.item_story, parent, false)
        return StoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: StoryViewHolder, position: Int) {
        val story = stories[position]
        val uri = story.uri
        val key = uri.toString()

        Glide.with(context).load(uri).into(holder.mediaView)
        holder.playIcon.visibility =
            if (story.type == "video") View.VISIBLE else View.GONE

        val fileName = downloadedMap[uri.toString()]

        val isDownloaded = fileName != null && (
                downloadedMap.containsKey(uri.toString()) ||
                        isFileExistsInGallery(fileName)
                )

        holder.downloadIcon.setImageResource(
            if (isDownloaded)
                R.drawable.ic_checked
            else
                R.drawable.ic_download2
        )

        // ===== FULLSCREEN =====
        holder.playIcon.setOnClickListener {
            showFullscreenPlayer(uri)
        }

        holder.mediaView.setOnClickListener {
            if (story.type == "image") {
                showFullscreenImage(uri)
            }
        }

        // ===== DOWNLOAD =====
        holder.downloadButton.setOnClickListener {

            val fileName = downloadedMap[uri.toString()]

            val alreadyDownloaded = fileName != null && (
                    downloadedMap.containsKey(uri.toString()) ||
                            isFileExistsInGallery(fileName)
                    )

            if (alreadyDownloaded) {
                Toast.makeText(
                    context,
                    "Story sudah diunduh",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            saveStoryToGalleryAsync(uri) { fileName ->
                downloadedMap[uri.toString()] = fileName
                notifyItemChanged(holder.bindingAdapterPosition)
            }
        }
    }
    private fun isFileExistsInGallery(fileName: String): Boolean {
        val projection = arrayOf(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)

        val selection = "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(fileName)

        val uri = android.provider.MediaStore.Files.getContentUri("external")

        context.contentResolver.query(
            uri,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            return cursor.count > 0
        }

        return false
    }
    override fun getItemCount(): Int = stories.size

    // =======================
    // DOWNLOAD
    // =======================
    private fun saveStoryToGalleryAsync(
        uri: Uri,
        onSuccess: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val resolver = context.contentResolver
                val mimeType = resolver.getType(uri) ?: "image/jpeg"

                val ext = MimeTypeMap.getSingleton()
                    .getExtensionFromMimeType(mimeType) ?: "jpg"

                val fileName =
                    "WhatsAppStory_${System.currentTimeMillis()}.$ext"

                StorySaver.saveToGallery(
                    context = context,
                    sourceUri = uri,
                    originalFileName = fileName,
                    mimeType = mimeType
                )

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Story berhasil disimpan",
                        Toast.LENGTH_SHORT
                    ).show()
                    onSuccess(fileName)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Gagal menyimpan story",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.e("StoryAdapter", "Download error", e)
                }
            }
        }
    }

    // =======================
    // VIEW HOLDER
    // =======================
    class StoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val mediaView: ImageView = itemView.findViewById(R.id.mediaView)
        val playIcon: ImageView = itemView.findViewById(R.id.playIcon)
        val downloadButton: FrameLayout = itemView.findViewById(R.id.downloadButton)
        val downloadIcon: ImageView = itemView.findViewById(R.id.downloadIcon)
    }

    // =======================
    // FULLSCREEN IMAGE
    // =======================
    private fun showFullscreenImage(uri: Uri) {
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val imageView = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
            scaleType = ImageView.ScaleType.MATRIX
            setImageURI(uri)
        }

        val matrix = Matrix()
        val savedMatrix = Matrix()

        var mode = 0
        val NONE = 0
        val DRAG = 1

        var startX = 0f
        var startY = 0f

        var minScale = 1f
        var maxScale = 3f
        var currentScale = 1f
        var isZoomed = false

        var imageWidth = 0f
        var imageHeight = 0f
        var viewWidth = 0
        var viewHeight = 0

        fun fixTranslation() {
            val values = FloatArray(9)
            matrix.getValues(values)

            val transX = values[Matrix.MTRANS_X]
            val transY = values[Matrix.MTRANS_Y]

            val scaledWidth = imageWidth * currentScale
            val scaledHeight = imageHeight * currentScale

            var fixX = 0f
            var fixY = 0f

            if (scaledWidth <= viewWidth) {
                fixX = (viewWidth - scaledWidth) / 2f - transX
            } else {
                if (transX > 0) fixX = -transX
                if (transX + scaledWidth < viewWidth)
                    fixX = viewWidth - (transX + scaledWidth)
            }

            if (scaledHeight <= viewHeight) {
                fixY = (viewHeight - scaledHeight) / 2f - transY
            } else {
                if (transY > 0) fixY = -transY
                if (transY + scaledHeight < viewHeight)
                    fixY = viewHeight - (transY + scaledHeight)
            }

            matrix.postTranslate(fixX, fixY)
        }

        imageView.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    imageView.viewTreeObserver.removeOnGlobalLayoutListener(this)

                    val d = imageView.drawable ?: return
                    imageWidth = d.intrinsicWidth.toFloat()
                    imageHeight = d.intrinsicHeight.toFloat()

                    viewWidth = imageView.width
                    viewHeight = imageView.height

                    minScale = minOf(
                        viewWidth / imageWidth,
                        viewHeight / imageHeight
                    )
                    currentScale = minScale

                    val dx = (viewWidth - imageWidth * currentScale) / 2f
                    val dy = (viewHeight - imageHeight * currentScale) / 2f

                    matrix.setScale(currentScale, currentScale)
                    matrix.postTranslate(dx, dy)
                    imageView.imageMatrix = matrix
                }
            }
        )

        val scaleDetector = ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val prevScale = currentScale
                    currentScale *= detector.scaleFactor

                    currentScale = currentScale.coerceIn(minScale, maxScale)
                    val factor = currentScale / prevScale

                    matrix.postScale(
                        factor,
                        factor,
                        detector.focusX,
                        detector.focusY
                    )
                    fixTranslation()
                    imageView.imageMatrix = matrix
                    isZoomed = currentScale > minScale + 0.01f
                    return true
                }
            }
        )

        val gestureDetector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (!isZoomed) {
                        val factor = maxScale / currentScale
                        matrix.postScale(factor, factor, e.x, e.y)
                        currentScale = maxScale
                        isZoomed = true
                    } else {
                        currentScale = minScale
                        val dx = (viewWidth - imageWidth * currentScale) / 2f
                        val dy = (viewHeight - imageHeight * currentScale) / 2f
                        matrix.setScale(currentScale, currentScale)
                        matrix.postTranslate(dx, dy)
                        isZoomed = false
                    }
                    imageView.imageMatrix = matrix
                    return true
                }
            }
        )

        imageView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)

            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    savedMatrix.set(matrix)
                    startX = event.x
                    startY = event.y
                    mode = DRAG
                }

                MotionEvent.ACTION_MOVE -> {
                    if (mode == DRAG && isZoomed) {
                        matrix.set(savedMatrix)
                        matrix.postTranslate(
                            event.x - startX,
                            event.y - startY
                        )
                        fixTranslation()
                        imageView.imageMatrix = matrix
                    }
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_POINTER_UP -> mode = NONE
            }
            true
        }

        dialog.setContentView(imageView)
        dialog.show()
    }


    // =======================
    // FULLSCREEN VIDEO
    // =======================
// EXOPLAYER fullscreen implementation
    private fun showFullscreenPlayer(uri: Uri) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_video_player, null)
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(dialogView)

        // Note: ensure your dialog layout uses PlayerView with id fullscreenVideoView
        val playerView: PlayerView = dialogView.findViewById(R.id.fullscreenVideoView)
        val playPauseButton: ImageButton = dialogView.findViewById(R.id.playPauseButton)
        val closeButton: ImageButton = dialogView.findViewById(R.id.closeButton)
        val videoSeekBar: SeekBar = dialogView.findViewById(R.id.videoSeekBar)
        val currentTime: TextView = dialogView.findViewById(R.id.currentTime)
        val totalTime: TextView = dialogView.findViewById(R.id.totalTime)
        val overlay: View = dialogView.findViewById(R.id.overlay)
        val controlPanel: View = dialogView.findViewById(R.id.controlPanel)
        val downloadButton: TextView = dialogView.findViewById(R.id.downloadButton)

        downloadButton.setOnClickListener {
            saveStoryToGalleryAsync(uri) {
            }
        }
        // Build ExoPlayer
        val exoPlayer = ExoPlayer.Builder(context).build()
        playerView.player = exoPlayer

        // Prepare media
        val mediaItem = MediaItem.fromUri(uri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        // Handler untuk update UI
        val handler = Handler(Looper.getMainLooper())
        val updateRunnable = object : Runnable {
            override fun run() {
                try {
                    val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                    val dur = exoPlayer.duration.coerceAtLeast(0L)
                    if (dur > 0) {
                        val progress = ((pos * 100) / dur).toInt()
                        videoSeekBar.progress = progress
                        currentTime.text = formatTime(pos.toInt())
                    }
                } catch (e: Exception) {
                    // ignore occasionally when player not ready
                }
                handler.postDelayed(this, 500)
            }
        }

        fun startUpdates() { handler.post(updateRunnable) }
        fun stopUpdates() { handler.removeCallbacks(updateRunnable) }

        // Player listener
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    val dur = exoPlayer.duration
                    if (dur > 0) totalTime.text = formatTime(dur.toInt())
                    startUpdates()
                } else if (state == Player.STATE_ENDED) {
                    playPauseButton.setImageResource(android.R.drawable.ic_media_play)
                    videoSeekBar.progress = 100
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playPauseButton.setImageResource(
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                )
            }
        })

        // Play/pause
        playPauseButton.setOnClickListener {
            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
        }

        // Controls toggling
        var isControlVisible = false
        fun showControlsTemporarily() {
            controlPanel.visibility = View.VISIBLE
            isControlVisible = true
            handler.postDelayed({
                controlPanel.visibility = View.GONE
                isControlVisible = false
            }, 3000)
        }

        overlay.setOnClickListener {
            if (!isControlVisible) showControlsTemporarily() else {
                controlPanel.visibility = View.GONE
                isControlVisible = false
            }
        }

        // Scrubbing logic: throttle seeks while dragging
        var pendingSeekPosition: Long = 0L
        var seekScheduled = false
        val seekHandler = Handler(Looper.getMainLooper())
        val seekRunnable = Runnable {
            try {
                exoPlayer.seekTo(pendingSeekPosition)
            } catch (e: Exception) {
                Log.e("StoryAdapter", "ExoPlayer seek error", e)
            }
            seekScheduled = false
        }

        videoSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            var userSeeking = false
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val dur = exoPlayer.duration.takeIf { it > 0 } ?: return
                    val newPos = (progress * dur) / 100
                    // update UI waktu saat drag
                    currentTime.text = formatTime(newPos.toInt())
                    pendingSeekPosition = newPos
                    // schedule a throttled seek while dragging (150ms)
                    if (seekScheduled) {
                        seekHandler.removeCallbacks(seekRunnable)
                    }
                    seekScheduled = true
                    seekHandler.postDelayed(seekRunnable, 150)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                userSeeking = true
                stopUpdates()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                userSeeking = false
                if (seekScheduled) {
                    seekHandler.removeCallbacks(seekRunnable)
                    seekScheduled = false
                }
                try {
                    exoPlayer.seekTo(pendingSeekPosition)
                } catch (e: Exception) {
                    Log.e("StoryAdapter", "ExoPlayer final seek error", e)
                }
                startUpdates()
            }
        })

        closeButton.setOnClickListener {
            stopUpdates()
            try { exoPlayer.stop() } catch (_: Exception) {}
            exoPlayer.release()
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            stopUpdates()
            try { exoPlayer.stop() } catch (_: Exception) {}
            exoPlayer.release()
        }

        dialog.show()
    }

    private fun formatTime(ms: Int): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    fun refreshDownloadState() {
        notifyDataSetChanged()
    }
}
