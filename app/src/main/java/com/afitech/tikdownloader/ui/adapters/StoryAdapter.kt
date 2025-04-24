package com.afitech.tikdownloader.ui.adapters

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.view.*
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.afitech.tikdownloader.R
import com.afitech.tikdownloader.data.StorySaver
import com.afitech.tikdownloader.data.model.StoryItem
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
                StorySaver.saveToGallery(context, uri)
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
        val dialog = android.app.Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(dialogView)

        val videoView: VideoView = dialogView.findViewById(R.id.fullscreenVideoView)
        val playPauseButton: ImageButton = dialogView.findViewById(R.id.playPauseButton)
        val closeButton: ImageButton = dialogView.findViewById(R.id.closeButton)

        videoView.setVideoURI(uri)
        videoView.setOnCompletionListener {
            playPauseButton.setImageResource(android.R.drawable.ic_media_play)
        }

        playPauseButton.setOnClickListener {
            if (videoView.isPlaying) {
                videoView.pause()
                playPauseButton.setImageResource(android.R.drawable.ic_media_play)
            } else {
                videoView.start()
                playPauseButton.setImageResource(android.R.drawable.ic_media_pause)
            }
        }

        closeButton.setOnClickListener {
            videoView.stopPlayback()
            dialog.dismiss()
        }

        dialog.show()
    }

}