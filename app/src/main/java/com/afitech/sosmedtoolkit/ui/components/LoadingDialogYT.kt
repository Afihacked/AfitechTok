package com.afitech.sosmedtoolkit.ui.components

import android.app.Dialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import com.afitech.sosmedtoolkit.R

object LoadingDialogYT {
    private var dialog: Dialog? = null
    private var handler: Handler? = null
    private var showSabarRunnable: Runnable? = null

    fun show(context: Context) {
        if (dialog?.isShowing == true) return

        dialog = Dialog(context).apply {
            setContentView(LayoutInflater.from(context).inflate(R.layout.dialog_loading_yt, null))
            setCancelable(false)
            window?.setLayout(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.MATCH_PARENT
            )
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            show()
        }

        // Timer 3 detik untuk menampilkan "Sabar ya..."
        handler = Handler(Looper.getMainLooper())
        val sabarText: View? = dialog?.findViewById(R.id.textSabar)
        showSabarRunnable = Runnable {
            sabarText?.visibility = View.VISIBLE
        }
        handler?.postDelayed(showSabarRunnable!!, 6000)
    }

    fun dismiss() {
        handler?.removeCallbacks(showSabarRunnable!!)
        dialog?.dismiss()
        dialog = null
    }
}
