package com.afitech.afitechtok.ui.fragments

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.afitech.afitechtok.R
import com.afitech.afitechtok.ui.helpers.RestartReceiver
import com.afitech.afitechtok.utils.areAdsEnabled
import com.afitech.afitechtok.utils.setAdsEnabled
import com.afitech.afitechtok.utils.setStatusBarColorRes

class SettingsFragment : Fragment() {

    private lateinit var switchAds: SwitchCompat
    private lateinit var tvDescription: TextView

    // menyimpan state sebelumnya agar bisa rollback bila user batal
    private var previousChecked: Boolean = false

    // flag untuk menandai perubahan programatik (agar tidak memicu dialog)
    private var internalChange = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // jika fragment menempatkan toolbar yang menjulur ke statusbar, gunakan drawBehind = true
        setStatusBarColorRes(R.color.white, isLightStatusBar = true, drawBehind = true)

        // Inisialisasi UI
        switchAds = view.findViewById(R.id.switchAds)
        tvDescription = view.findViewById(R.id.tvAdsDescription)

        // Ambil status dari SharedPreferences (read actual state)
        val adsDisabled = !requireContext().areAdsEnabled()
        switchAds.isChecked = adsDisabled
        previousChecked = adsDisabled
        updateDescription(adsDisabled)

        // Pasang listener sekali — gunakan flag internalChange untuk rollback programatik
        switchAds.setOnCheckedChangeListener { _, isChecked ->
            if (internalChange) {
                // Ini perubahan programatik, abaikan dan reset flag
                internalChange = false
                return@setOnCheckedChangeListener
            }

            // ambil state aktual saat ini dari prefs (defensive)
            val currentActual = !requireContext().areAdsEnabled()
            // simpan nilai lama aktual supaya bisa rollback bila user batal
            val old = currentActual

            // tampilkan dialog konfirmasi restart
            showRestartConfirmDialog(isChecked, old)
        }
    }

    /**
     * isChecked = nilai baru yang di-request user pada Switch (true = adsDisabled)
     * oldChecked = nilai aktual saat ini sebelum perubahan (true = adsDisabled)
     */
    private fun showRestartConfirmDialog(isChecked: Boolean, oldChecked: Boolean) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setMessage("Perubahan akan diterapkan setelah aplikasi di-restart. Restart sekarang?")

        // Buat tombol tapi jangan langsung show — kita perlu akses button setelah show untuk mewarnai
        builder.setPositiveButton("Restart Sekarang") { _, _ -> /* handled below */ }
        builder.setNegativeButton("Nanti") { _, _ -> /* handled below */ }
        builder.setNeutralButton("Batal") { _, _ -> /* handled below */ }

        val dialog = builder.create()
        dialog.setCancelable(true)
        dialog.setOnShowListener {
            // ambil warna primary dari resources
            val color = ContextCompat.getColor(requireContext(), R.color.colorPrimary)

            // Tombol positif (Restart Sekarang)
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positive.setTextColor(color)
            positive.setOnClickListener {
                // Simpan preference (mapping: switch checked = adsDisabled)
                requireContext().setAdsEnabled(!isChecked)
                updateDescription(isChecked)
                previousChecked = isChecked

                // --- RELIABLE RESTART using AlarmManager -> BroadcastReceiver ---
                val receiverIntent = Intent(requireContext(), RestartReceiver::class.java)
                // optional: put extras if you need
                val pending = PendingIntent.getBroadcast(
                    requireContext(),
                    12345,
                    receiverIntent,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    else
                        PendingIntent.FLAG_CANCEL_CURRENT
                )

                val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                if (alarmManager != null) {
                    val triggerAt = System.currentTimeMillis() + 350L
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                        } else {
                            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                        }
                    } catch (e: Exception) {
                        // fallback: try to directly start receiver (best-effort)
                        try { requireContext().sendBroadcast(receiverIntent) } catch (_: Exception) {}
                    }
                } else {
                    // fallback
                    try { requireContext().sendBroadcast(receiverIntent) } catch (_: Exception) {}
                }

                // kill current process so AlarmManager can relaunch the app
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        Process.killProcess(Process.myPid())
                        System.exit(0)
                    } catch (_: Exception) {}
                }, 450L)

                dialog.dismiss()
            }

            // Tombol negatif (Nanti)
            val negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            negative.setTextColor(color)
            negative.setOnClickListener {
                // Simpan preference tapi tidak restart sekarang
                requireContext().setAdsEnabled(!isChecked)
                updateDescription(isChecked)
                previousChecked = isChecked
                Toast.makeText(requireContext(), "Perubahan akan aktif setelah restart aplikasi", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }

            // Tombol netral (Batal)
            val neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            neutral.setTextColor(color)
            neutral.setOnClickListener {
                // rollback ke nilai aktual sebelumnya (tidak menyimpan)
                internalChange = true
                switchAds.isChecked = oldChecked
                dialog.dismiss()
            }
        }

        dialog.setOnCancelListener {
            // jika dialog ditutup, rollback ke nilai aktual sebelumnya
            internalChange = true
            switchAds.isChecked = oldChecked
        }

        dialog.show()
    }

    private fun updateDescription(adsDisabled: Boolean) {
        tvDescription.text = if (adsDisabled) {
            "Iklan telah dimatikan selama penggunaan aplikasi."
        } else {
            "Jika diaktifkan, iklan akan dimatikan selama penggunaan aplikasi."
        }
    }
}
