package com.afitech.tikdownloader.ui.components

import android.app.AlertDialog
import android.app.Dialog
import android.content.res.Configuration
import android.graphics.text.LineBreaker
import android.os.Bundle
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.afitech.tikdownloader.R

class GuideDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val textView = TextView(requireContext()).apply {
            val isDarkMode = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

            text = getString(R.string.panduan)
            textSize = 16f
            setPadding(70, 24, 70, 24)
            // Ambil warna sesuai tema
            setTextColor(ContextCompat.getColor(context, if (isDarkMode) android.R.color.white else android.R.color.black))

            // Justify Text
            justificationMode = LineBreaker.JUSTIFICATION_MODE_INTER_WORD
        }

        return AlertDialog.Builder(requireContext())
            .setTitle("Peringatan!")
            .setView(textView) // Gunakan custom TextView
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .create()
    }
}

