package com.afitech.sosmedtoolkit.ui.adapters

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.webkit.MimeTypeMap
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.afitech.sosmedtoolkit.R
import com.afitech.sosmedtoolkit.data.StorySaver
import com.afitech.sosmedtoolkit.data.model.StoryItem
import com.bumptech.glide.Glide
import kotlinx.coroutines.*

class StoryAdapter(
    private var stories: List<StoryItem>,
    private val context: Context
) : RecyclerView.Adapter<StoryAdapter.StoryViewHolder>() {

    private var currentPlayer: MediaPlayer? = null
    private var currentSurface: Surface? = null
    private var currentTexture: TextureView? = null
    private var currentPlayIcon: ImageView? = null

    fun updateList(newStories: List<StoryItem>) {
        stories = newStories
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoryViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_story, parent, false)
        return StoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: StoryViewHolder, position: Int) {
        val story = stories[position]
        val uri = story.uri

        Glide.with(context).load(uri).into(holder.mediaView)
        holder.videoTexture.visibility = View.GONE

        holder.playIcon.visibility = if (story.type == "video") View.VISIBLE else View.GONE

        // Hanya aktifkan klik jika story adalah video
        if (story.type == "video") {
            holder.playIcon.setOnClickListener {
                handleVideoClick(holder, uri)
            }

            // Toggle Play/Pause saat TextureView diklik
            holder.videoTexture.setOnClickListener {
                togglePlayPause(holder.playIcon)
            }
        }

        holder.downloadButton.setOnClickListener {
            saveStoryToGalleryAsync(uri)
        }

        holder.playIcon.setOnClickListener {
            showFullscreenPlayer(uri)
        }

        holder.mediaView.setOnClickListener {
            if (story.type == "image") {
                showFullscreenImage(story.uri)
            }
        }
    }

    override fun getItemCount(): Int = stories.size

    private fun handleVideoClick(holder: StoryViewHolder, uri: Uri) {
        releaseCurrentPlayer()

        holder.videoTexture.visibility = View.VISIBLE
        holder.playIcon.visibility = View.GONE
        currentPlayIcon = holder.playIcon
        currentTexture = holder.videoTexture

        holder.videoTexture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                val surface = Surface(surfaceTexture)
                currentSurface = surface
                currentPlayer = MediaPlayer().apply {
                    setDataSource(context, uri)
                    setSurface(surface)
                    isLooping = true
                    setOnPreparedListener {
                        it.start()
                    }
                    setOnCompletionListener {
                        it.seekTo(0)
                        it.start()
                    }
                    prepareAsync()
                }
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                releaseCurrentPlayer()
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
    }

    private fun togglePlayPause(playIcon: ImageView?) {
        currentPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                playIcon?.visibility = View.VISIBLE
            } else {
                it.start()
                playIcon?.visibility = View.GONE
            }
        }
    }
    private fun showFullscreenImage(uri: Uri) {
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)

        val imageView = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.MATRIX
            setBackgroundColor(Color.BLACK)
            setImageURI(uri)
        }

        val matrix = Matrix()
        val savedMatrix = Matrix()

        var startX = 0f
        var startY = 0f
        var mode = 0
        val NONE = 0
        val DRAG = 1

        var minScale = 1f
        var maxScale = 2f
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

            var fixTransX = 0f
            var fixTransY = 0f

            if (scaledWidth <= viewWidth) {
                fixTransX = (viewWidth - scaledWidth) / 2f - transX
            } else {
                if (transX > 0) fixTransX = -transX
                if (transX + scaledWidth < viewWidth) fixTransX = viewWidth - (transX + scaledWidth)
            }

            if (scaledHeight <= viewHeight) {
                fixTransY = (viewHeight - scaledHeight) / 2f - transY
            } else {
                if (transY > 0) fixTransY = -transY
                if (transY + scaledHeight < viewHeight) fixTransY = viewHeight - (transY + scaledHeight)
            }

            matrix.postTranslate(fixTransX, fixTransY)
        }

        imageView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                imageView.viewTreeObserver.removeOnGlobalLayoutListener(this)

                val drawable = imageView.drawable ?: return
                imageWidth = drawable.intrinsicWidth.toFloat()
                imageHeight = drawable.intrinsicHeight.toFloat()

                viewWidth = imageView.width
                viewHeight = imageView.height

                minScale = minOf(viewWidth / imageWidth, viewHeight / imageHeight)
                currentScale = minScale

                val dx = (viewWidth - imageWidth * currentScale) / 2f
                val dy = (viewHeight - imageHeight * currentScale) / 2f

                matrix.setScale(currentScale, currentScale)
                matrix.postTranslate(dx, dy)
                imageView.imageMatrix = matrix
            }
        })

        val gestureDetector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (!isZoomed) {
                        val focusX = e.x
                        val focusY = e.y
                        val scaleFactor = maxScale / currentScale
                        matrix.postScale(scaleFactor, scaleFactor, focusX, focusY)
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
                    fixTranslation()
                    imageView.imageMatrix = matrix
                    return true
                }
            }
        )

        imageView.setOnTouchListener { _, event ->
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
                        val dx = event.x - startX
                        val dy = event.y - startY
                        matrix.postTranslate(dx, dy)
                        fixTranslation()
                        imageView.imageMatrix = matrix
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    mode = NONE
                }
            }
            true
        }

        dialog.setOnKeyListener { _, keyCode, _ ->
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                dialog.dismiss()
                true
            } else {
                false
            }
        }

        dialog.setContentView(imageView)
        dialog.show()
    }

    private fun releaseCurrentPlayer() {
        currentPlayer?.stop()
        currentPlayer?.release()
        currentPlayer = null
        currentSurface?.release()
        currentSurface = null
        currentTexture?.visibility = View.GONE
        currentPlayIcon?.visibility = View.VISIBLE
    }

    private fun saveStoryToGalleryAsync(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val contentResolver = context.contentResolver
                val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
                // Bisa ambil nama file asli dari Uri, atau buat default saja:
                val originalFileName = "WhatsAppStory_${System.currentTimeMillis()}." +
                    MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)

                // Pastikan downloadHistoryDao sudah di-set di StorySaver sebelumnya
                StorySaver.saveToGallery(
                    context = context,
                    sourceUri = uri,
                    originalFileName = originalFileName,
                    mimeType = mimeType,
                    onProgressUpdate = { progress ->
                        // Optional: update progress jika perlu
                    }
                )

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Story berhasil disimpan", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal menyimpan story", Toast.LENGTH_SHORT).show()
                    Log.e("StoryAdapter", "Gagal menyimpan story: $uri", e)
                }
            }
        }
    }

    class StoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val mediaView: ImageView = itemView.findViewById(R.id.mediaView)
        val videoTexture: TextureView = itemView.findViewById(R.id.videoTexture)
        val playIcon: ImageView = itemView.findViewById(R.id.playIcon)
        val downloadButton: ImageButton = itemView.findViewById(R.id.downloadButton)
    }

    private fun showFullscreenPlayer(uri: Uri) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_video_player, null)
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(dialogView)

        val videoView: VideoView = dialogView.findViewById(R.id.fullscreenVideoView)
        val playPauseButton: ImageButton = dialogView.findViewById(R.id.playPauseButton)
        val closeButton: ImageButton = dialogView.findViewById(R.id.closeButton)
        val videoSeekBar: SeekBar = dialogView.findViewById(R.id.videoSeekBar)
        val currentTime: TextView = dialogView.findViewById(R.id.currentTime)
        val totalTime: TextView = dialogView.findViewById(R.id.totalTime)
        val overlay: View = dialogView.findViewById(R.id.overlay)
        val controlPanel: View = dialogView.findViewById(R.id.controlPanel)

        val handler = Handler(Looper.getMainLooper())
        var isControlVisible = false

        val updateSeekBarRunnable = object : Runnable {
            override fun run() {
                if (videoView.isPlaying) {
                    val position = videoView.currentPosition
                    val duration = videoView.duration
                    val progress = (position * 100) / duration
                    videoSeekBar.progress = progress
                    currentTime.text = formatTime(position)
                    handler.postDelayed(this, 500)
                }
            }
        }

        fun updateSeekBar() {
            handler.post(updateSeekBarRunnable)
        }

        videoView.setVideoURI(uri)
        videoView.setOnPreparedListener {
            videoView.start()
            playPauseButton.setImageResource(android.R.drawable.ic_media_pause)
            totalTime.text = formatTime(videoView.duration)
            updateSeekBar()

            // Tampilkan kontrol saat awal video dibuka
            if (!isControlVisible) {
                controlPanel.visibility = View.VISIBLE
                isControlVisible = true
                handler.postDelayed({
                    controlPanel.visibility = View.GONE
                    isControlVisible = false
                }, 3000)
            }
        }

        fun toggleControls() {
            isControlVisible = !isControlVisible
            controlPanel.visibility = if (isControlVisible) View.VISIBLE else View.GONE

            if (isControlVisible) {
                handler.postDelayed({
                    controlPanel.visibility = View.GONE
                    isControlVisible = false
                }, 3000)
            }
        }

        overlay.setOnClickListener { toggleControls() }

        playPauseButton.setOnClickListener {
            if (videoView.isPlaying) {
                videoView.pause()
                playPauseButton.setImageResource(android.R.drawable.ic_media_play)
            } else {
                videoView.start()
                playPauseButton.setImageResource(android.R.drawable.ic_media_pause)
            }
        }

        videoSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val newPosition = (progress * videoView.duration) / 100
                    videoView.seekTo(newPosition)
                    currentTime.text = formatTime(newPosition)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        videoView.setOnCompletionListener {
            playPauseButton.setImageResource(android.R.drawable.ic_media_play)
            videoSeekBar.progress = 100
            currentTime.text = formatTime(videoView.duration)
        }

        closeButton.setOnClickListener {
            handler.removeCallbacks(updateSeekBarRunnable)
            videoView.stopPlayback()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun formatTime(ms: Int): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}
