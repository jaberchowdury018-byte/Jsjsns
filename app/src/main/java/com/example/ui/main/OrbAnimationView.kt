package com.example.ui.main

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin

class OrbAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class State {
        IDLE, LISTENING, SPEAKING, THINKING, ACTIVE
    }

    private var currentState = State.IDLE
    private var pulseScale = 1.0f
    private var glowAlpha = 150
    private var rotationAngle = 0f
    private var waveOffset = 0f
    private var thinkingAngle = 0f
    private var amplitude = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var pulseAnimator: ValueAnimator? = null
    private var glowAnimator: ValueAnimator? = null
    private var rotationAnimator: ValueAnimator? = null
    private var waveAnimator: ValueAnimator? = null
    private var thinkingAnimator: ValueAnimator? = null

    init {
        ringPaint.pathEffect = DashPathEffect(floatArrayOf(15f, 15f), 0f)
        startAnimators()
    }

    fun setState(state: State) {
        if (currentState == state) return
        currentState = state
        resetAnimatorsForState()
        postInvalidate()
    }

    fun setAmplitude(amp: Float) {
        this.amplitude = amp.coerceIn(0f, 1f)
        postInvalidate()
    }

    private fun startAnimators() {
        pulseAnimator = ValueAnimator.ofFloat(1.0f, 1.15f, 1.0f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                pulseScale = it.animatedValue as Float
                postInvalidate()
            }
            start()
        }

        glowAnimator = ValueAnimator.ofInt(120, 220, 120).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                glowAlpha = it.animatedValue as Int
                postInvalidate()
            }
            start()
        }

        rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 6000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                rotationAngle = it.animatedValue as Float
                postInvalidate()
            }
            start()
        }

        waveAnimator = ValueAnimator.ofFloat(0f, 2f * Math.PI.toFloat()).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                waveOffset = it.animatedValue as Float
                postInvalidate()
            }
            start()
        }

        thinkingAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                thinkingAngle = it.animatedValue as Float
                postInvalidate()
            }
            start()
        }
    }

    private fun resetAnimatorsForState() {
        pulseAnimator?.cancel()
        glowAnimator?.cancel()
        rotationAnimator?.cancel()
        waveAnimator?.cancel()
        thinkingAnimator?.cancel()

        when (currentState) {
            State.IDLE -> {
                pulseAnimator?.duration = 1500
                glowAnimator?.duration = 1500
                pulseAnimator?.start()
                glowAnimator?.start()
                rotationAnimator?.duration = 6000
                rotationAnimator?.start()
            }
            State.LISTENING -> {
                pulseAnimator?.duration = 1000
                pulseAnimator?.start()
                glowAnimator?.duration = 1000
                glowAnimator?.start()
                rotationAnimator?.duration = 3000
                rotationAnimator?.start()
                waveAnimator?.duration = 800
                waveAnimator?.start()
            }
            State.SPEAKING -> {
                pulseAnimator?.duration = 800
                pulseAnimator?.start()
                glowAnimator?.duration = 800
                glowAnimator?.start()
                rotationAnimator?.duration = 2000
                rotationAnimator?.start()
                waveAnimator?.duration = 500
                waveAnimator?.start()
            }
            State.THINKING -> {
                thinkingAnimator?.start()
            }
            State.ACTIVE -> {
                pulseAnimator?.duration = 1200
                pulseAnimator?.start()
                glowAnimator?.duration = 1200
                glowAnimator?.start()
                rotationAnimator?.duration = 4000
                rotationAnimator?.start()
                waveAnimator?.duration = 1000
                waveAnimator?.start()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val baseRadius = (width.coerceAtMost(height) / 4f) * pulseScale

        if (baseRadius <= 0) return

        // 1. Radial glow layer
        val glowColorStart = getGlowColorStart()
        val glowColorEnd = Color.TRANSPARENT
        paint.shader = RadialGradient(
            cx, cy, baseRadius * 1.6f,
            glowColorStart, glowColorEnd, Shader.TileMode.CLAMP
        )
        paint.alpha = glowAlpha
        canvas.drawCircle(cx, cy, baseRadius * 1.6f, paint)
        paint.alpha = 255

        // 2. Core orb
        val coreColorStart = getCoreColorStart()
        val coreColorEnd = getCoreColorEnd()
        paint.shader = RadialGradient(
            cx - baseRadius * 0.2f, cy - baseRadius * 0.2f, baseRadius,
            coreColorStart, coreColorEnd, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, baseRadius, paint)
        paint.shader = null

        // 3. 3 rotating rings
        ringPaint.color = getAccentColor()
        canvas.save()
        canvas.rotate(rotationAngle, cx, cy)
        canvas.drawCircle(cx, cy, baseRadius * 1.2f, ringPaint)
        canvas.restore()

        canvas.save()
        canvas.rotate(-rotationAngle * 1.5f, cx, cy)
        canvas.drawCircle(cx, cy, baseRadius * 1.35f, ringPaint)
        canvas.restore()

        canvas.save()
        canvas.rotate(rotationAngle * 0.8f, cx, cy)
        canvas.drawCircle(cx, cy, baseRadius * 1.5f, ringPaint)
        canvas.restore()

        // 4. Wave rings (sine waves)
        if (currentState == State.LISTENING || currentState == State.SPEAKING || currentState == State.ACTIVE) {
            wavePaint.color = getAccentColor()
            val path = Path()
            val points = 60
            val waveAmp = if (currentState == State.SPEAKING) 15f + amplitude * 30f else 10f
            for (i in 0..points) {
                val angle = i * (360f / points)
                val rad = Math.toRadians(angle.toDouble()).toFloat()
                val waveOffsetRad = waveOffset + angle * 4f * (Math.PI.toFloat() / 180f)
                val r = baseRadius * 1.1f + sin(waveOffsetRad.toDouble()).toFloat() * waveAmp
                val x = cx + r * cos(rad)
                val y = cy + r * sin(rad)
                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            path.close()
            canvas.drawPath(path, wavePaint)
        }

        // 5. Thinking arc
        if (currentState == State.THINKING) {
            ringPaint.color = Color.parseColor("#40C4FF")
            val rect = RectF(cx - baseRadius * 1.2f, cy - baseRadius * 1.2f, cx + baseRadius * 1.2f, cy + baseRadius * 1.2f)
            canvas.drawArc(rect, thinkingAngle, 90f, false, ringPaint)
            canvas.drawArc(rect, thinkingAngle + 180f, 90f, false, ringPaint)
        }

        // 6. Particles
        if (currentState == State.SPEAKING || currentState == State.ACTIVE) {
            particlePaint.color = getAccentColor()
            val count = 12
            val radiusOffset = if (currentState == State.SPEAKING) baseRadius * 1.4f + amplitude * 20f else baseRadius * 1.4f
            for (i in 0 until count) {
                val angle = i * (360f / count) + rotationAngle
                val rad = Math.toRadians(angle.toDouble()).toFloat()
                val px = cx + radiusOffset * cos(rad)
                val py = cy + radiusOffset * sin(rad)
                val pRadius = if (currentState == State.SPEAKING) 4f.dpToPx() + amplitude * 6f.dpToPx() else 5f.dpToPx()
                canvas.drawCircle(px, py, pRadius, particlePaint)
            }
        }

        // 7. Inner Highlight
        paint.shader = RadialGradient(
            cx - baseRadius * 0.4f, cy - baseRadius * 0.4f, baseRadius * 0.5f,
            Color.argb(180, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, baseRadius, paint)
        paint.shader = null
    }

    private fun getGlowColorStart(): Int {
        return when (currentState) {
            State.IDLE -> Color.parseColor("#B71C1C")
            State.LISTENING, State.ACTIVE -> Color.parseColor("#FF1744")
            State.SPEAKING -> Color.parseColor("#E040FB")
            State.THINKING -> Color.parseColor("#40C4FF")
        }
    }

    private fun getCoreColorStart(): Int {
        return when (currentState) {
            State.IDLE -> Color.parseColor("#E53935")
            State.LISTENING, State.ACTIVE -> Color.parseColor("#FF5252")
            State.SPEAKING -> Color.parseColor("#F48FB1")
            State.THINKING -> Color.parseColor("#80D8FF")
        }
    }

    private fun getCoreColorEnd(): Int {
        return when (currentState) {
            State.IDLE -> Color.parseColor("#880E4F")
            State.LISTENING, State.ACTIVE -> Color.parseColor("#D500F9")
            State.SPEAKING -> Color.parseColor("#FF1744")
            State.THINKING -> Color.parseColor("#00B0FF")
        }
    }

    private fun getAccentColor(): Int {
        return when (currentState) {
            State.IDLE -> Color.parseColor("#B71C1C")
            State.LISTENING, State.ACTIVE -> Color.parseColor("#FF1744")
            State.SPEAKING -> Color.parseColor("#D500F9")
            State.THINKING -> Color.parseColor("#00B0FF")
        }
    }

    private fun Float.dpToPx(): Float = this * resources.displayMetrics.density
    private fun Int.dpToPx(): Float = this * resources.displayMetrics.density
}
