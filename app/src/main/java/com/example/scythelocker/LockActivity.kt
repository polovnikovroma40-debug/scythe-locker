package com.example.scythelocker

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Единственный экран приложения: показывает силуэт смерти с косой,
 * часы и PIN-клавиатуру.
 *
 * Пароль жёстко задан в коде (см. FIXED_PIN ниже) — экрана "придумать
 * PIN" больше нет. Правильный код закрывает экран (finish()),
 * неправильный — встряхивает точки.
 *
 * Это учебный/демо-проект: сам по себе он не "перекрывает" сторонние
 * приложения на экране — для полноценного App Lock поверх других
 * приложений потребуется AccessibilityService или оверлей-разрешение
 * (SYSTEM_ALERT_WINDOW) — см. комментарий в конце файла.
 */
class LockActivity : AppCompatActivity() {

    private val enteredPin = StringBuilder()

    private lateinit var pinDotsContainer: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var reaperImage: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)

        pinDotsContainer = findViewById(R.id.pin_dots)
        statusText = findViewById(R.id.status_text)
        reaperImage = findViewById(R.id.reaper)

        statusText.text = getString(R.string.enter_pin)

        buildPinDots(PIN_LENGTH)
        buildKeypad()
        animateReaper()
    }

    // ---------- UI construction ----------

    private fun buildPinDots(count: Int) {
        pinDotsContainer.removeAllViews()
        val size = dp(14)
        val margin = dp(8)
        repeat(count) {
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).also { lp ->
                    lp.marginStart = margin
                    lp.marginEnd = margin
                }
                setBackgroundResource(R.drawable.pin_dot_empty)
            }
            pinDotsContainer.addView(dot)
        }
    }

    private fun refreshPinDots() {
        for (i in 0 until pinDotsContainer.childCount) {
            val dot = pinDotsContainer.getChildAt(i)
            dot.setBackgroundResource(
                if (i < enteredPin.length) R.drawable.pin_dot_filled else R.drawable.pin_dot_empty
            )
        }
    }

    private fun buildKeypad() {
        val keypad = findViewById<GridLayout>(R.id.keypad)
        keypad.removeAllViews()
        val labels = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")

        for (label in labels) {
            val cellSize = dp(76)
            val key = TextView(this).apply {
                text = label
                textSize = 22f
                setTextColor(resources.getColor(R.color.text_light, theme))
                gravity = android.view.Gravity.CENTER
                layoutParams = GridLayout.LayoutParams().apply {
                    width = cellSize
                    height = cellSize
                    setMargins(dp(6), dp(6), dp(6), dp(6))
                }
                if (label.isNotEmpty()) {
                    setBackgroundResource(R.drawable.key_bg)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        performHaptic(it)
                        onKeyPressed(label)
                    }
                }
            }
            keypad.addView(key)
        }
    }

    private fun performHaptic(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    // ---------- PIN logic ----------

    private fun onKeyPressed(label: String) {
        when (label) {
            "⌫" -> {
                if (enteredPin.isNotEmpty()) enteredPin.deleteCharAt(enteredPin.length - 1)
                refreshPinDots()
            }
            else -> {
                if (enteredPin.length < PIN_LENGTH) {
                    enteredPin.append(label)
                    refreshPinDots()
                    if (enteredPin.length == PIN_LENGTH) {
                        checkPin()
                    }
                }
            }
        }
    }

    private fun checkPin() {
        if (enteredPin.toString() == FIXED_PIN) {
            statusText.text = "Добро пожаловать"
            reaperImage.postDelayed({ finish() }, 300)
        } else {
            statusText.text = getString(R.string.wrong_pin)
            shakePinDots()
            enteredPin.clear()
            pinDotsContainer.postDelayed({ refreshPinDots() }, 250)
        }
    }

    private fun shakePinDots() {
        val shake = ObjectAnimator.ofFloat(
            pinDotsContainer, "translationX",
            0f, -24f, 24f, -18f, 18f, -8f, 8f, 0f
        )
        shake.duration = 400
        shake.start()
    }

    // ---------- Decorative animation ----------

    private fun animateReaper() {
        val breathe = ObjectAnimator.ofFloat(reaperImage, "scaleX", 1f, 1.04f, 1f).apply {
            duration = 3200
            repeatCount = ObjectAnimator.INFINITE
        }
        val breatheY = ObjectAnimator.ofFloat(reaperImage, "scaleY", 1f, 1.04f, 1f).apply {
            duration = 3200
            repeatCount = ObjectAnimator.INFINITE
        }
        val sway = ObjectAnimator.ofFloat(reaperImage, "rotation", -1.5f, 1.5f, -1.5f).apply {
            duration = 4200
            repeatCount = ObjectAnimator.INFINITE
        }
        AnimatorSet().apply {
            playTogether(breathe, breatheY, sway)
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        // Пароль жёстко задан здесь. Чтобы сменить его позже — поменяйте
        // только эту строку (и, при необходимости, PIN_LENGTH).
        private const val FIXED_PIN = "549901231"
        private val PIN_LENGTH = FIXED_PIN.length
    }
}

/*
 * РАСШИРЕНИЕ ДО "НАСТОЯЩЕГО" APP LOCK (поверх других приложений):
 *
 * Этот экран сам по себе — просто Activity. Чтобы он реально перекрывал
 * ЛЮБОЕ стороннее приложение (как настоящий App Lock), нужно дополнительно:
 *
 * 1. AccessibilityService, который слушает TYPE_WINDOW_STATE_CHANGED
 *    и по packageName целевого приложения запускает LockActivity поверх
 *    (FLAG_ACTIVITY_NEW_TASK), если приложение в списке "заблокированных".
 *
 * 2. Либо разрешение SYSTEM_ALERT_WINDOW + Service с WindowManager,
 *    рисующий этот же layout как оверлей (TYPE_APPLICATION_OVERLAY).
 *
 * Оба варианта требуют отдельного запроса специальных разрешений у
 * пользователя в системных настройках Android (Accessibility /
 * "Отображение поверх других приложений") — их нельзя получить автоматически.
 * Спросите, если нужно добавить один из этих модулей.
 */
