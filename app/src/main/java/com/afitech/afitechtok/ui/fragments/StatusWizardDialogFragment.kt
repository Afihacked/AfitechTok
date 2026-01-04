package com.afitech.afitechtok.ui.fragments

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.afitech.afitechtok.R
import com.afitech.afitechtok.ui.helpers.StepType
import com.afitech.afitechtok.ui.helpers.WizardStep
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.view.animation.DecelerateInterpolator

class StatusWizardDialogFragment(
    private val steps: List<WizardStep>,
    private val currentStep: StepType,
    private val onContinue: () -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val view = layoutInflater.inflate(R.layout.dialog_status_wizard, null)

        val scrollView =
            view.findViewById<androidx.core.widget.NestedScrollView>(R.id.scrollContainer)
        val container = view.findViewById<LinearLayout>(R.id.stepContainer)
        val btnContinue = view.findViewById<View>(R.id.btnContinue)
        val stepCounter = view.findViewById<TextView>(R.id.tvStepCounter)

        val activeIndex = steps.indexOfFirst { it.stepType == currentStep }
        stepCounter.text = "Langkah ${activeIndex + 1} dari ${steps.size}"

        var activeStepView: View? = null

        steps.forEachIndexed { index, step ->

            val item = layoutInflater.inflate(
                R.layout.item_wizard_step,
                container,
                false
            )

            val icon = item.findViewById<ImageView>(R.id.icon)
            val title = item.findViewById<TextView>(R.id.title)
            val desc = item.findViewById<TextView>(R.id.desc)
            val indicator = item.findViewById<View>(R.id.indicator)

            icon.setImageResource(step.iconRes)
            title.text = step.title
            desc.text = step.description

            val isActive = index == activeIndex
            val isDone = index < activeIndex

            // 🔵 INDICATOR STATE
            indicator.setBackgroundResource(
                when {
                    isActive || isDone -> R.drawable.bg_step_active
                    else -> R.drawable.bg_step_idle
                }
            )

            // 🎨 VISUAL EMPHASIS
            item.alpha = when {
                isActive -> 1f
                isDone -> 0.75f
                else -> 0.45f
            }

            icon.alpha = item.alpha
            title.alpha = if (isActive) 1f else 0.7f
            desc.alpha = if (isActive) 0.9f else 0.4f

            container.addView(item)

            // 🎬 ANIMASI MASUK
            animateStepIn(item, index)

            // ✨ MICRO INTERACTION + TRACK ACTIVE VIEW
            if (isActive) {
                activeStepView = item

                item.postDelayed({
                    item.animate()
                        .scaleX(1.04f)
                        .scaleY(1.04f)
                        .setDuration(160)
                        .withEndAction {
                            item.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(160)
                                .start()
                        }
                        .start()
                }, index * 120L + 200)
            }
        }

        // 🚀 AUTO SCROLL KE STEP AKTIF
        view.postDelayed({
            activeStepView?.let { target ->
                scrollView.smoothScrollTo(
                    0,
                    target.top - 24
                )
            }
        }, 380)

        btnContinue.setOnClickListener {
            dismiss()
            onContinue()
        }

        return MaterialAlertDialogBuilder(
            requireContext(),
            R.style.ThemeOverlay_TikDownloader_MaterialAlertDialog
        )
            .setView(view)
            .create()
    }

    // 🎬 FADE + SLIDE ANIMATION
    private fun animateStepIn(view: View, index: Int) {
        view.alpha = 0f
        view.translationY = 24f

        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(index * 120L)
            .setDuration(280)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }
}
