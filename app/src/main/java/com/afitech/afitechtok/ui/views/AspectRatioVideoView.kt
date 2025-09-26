package com.afitech.afitechtok.ui.views

import android.content.Context
import android.util.AttributeSet
import android.widget.VideoView

class AspectRatioVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : VideoView(context, attrs) {

    private var videoWidth = 0
    private var videoHeight = 0

    init {
        // Tangkap dimensi video saat sudah siap
        setOnPreparedListener { mp ->
            videoWidth = mp.videoWidth
            videoHeight = mp.videoHeight
            // Minta layout ulang agar onMeasure dipanggil ulang
            requestLayout()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (videoWidth == 0 || videoHeight == 0) {
            // Belum siap, fallback default
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        val parentWidth = MeasureSpec.getSize(widthMeasureSpec)
        val parentHeight = MeasureSpec.getSize(heightMeasureSpec)
        val parentRatio = parentWidth.toFloat() / parentHeight
        val videoRatio = videoWidth.toFloat() / videoHeight

        val (finalWidth, finalHeight) = if (videoRatio > parentRatio) {
            // Video lebih lebar daripada screen → penuhi width, scale height
            parentWidth to (parentWidth / videoRatio).toInt()
        } else {
            // Video lebih tinggi daripada screen → penuhi height, scale width
            (parentHeight * videoRatio).toInt() to parentHeight
        }

        setMeasuredDimension(finalWidth, finalHeight)
    }
}
