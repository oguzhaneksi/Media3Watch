package com.media3watch.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.media3watch.sdk.model.SessionSnapshot
import java.util.Locale
import kotlin.math.pow

internal class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    config: OverlayConfig = OverlayConfig(),
) : FrameLayout(context, attrs, defStyleAttr) {

    private val marginPx = dp(12)
    private val handleSizePx = dp(24)
    private val pillCornerRadiusPx = dp(16).toFloat()
    private val cardCornerRadiusPx = dp(10).toFloat()
    private val cardElevationPx = dp(6).toFloat()

    private var safeInsetLeft = 0
    private var safeInsetTop = 0
    private var safeInsetRight = 0
    private var safeInsetBottom = 0

    private var currentState = config.initialState
    private var currentPosition = rememberedPosition ?: config.initialPosition
    private var sessionId: String? = null

    private var pendingSnapshot: SessionSnapshot? = null
    private var updatePosted = false

    private var dragging = false
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var downRawX = 0f
    private var downRawY = 0f

    private var currentPillColor = COLOR_HEALTH_GREEN
    private val pillBackground = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = pillCornerRadiusPx
        setColor(currentPillColor)
    }

    private val widgetContainer = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        clipToPadding = false
    }

    private val contentContainer = FrameLayout(context).apply {
        clipToPadding = false
    }

    private val collapsedText = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 12f
        setPadding(dp(10), dp(6), dp(10), dp(6))
        background = pillBackground
        text = "▶ IDLE | Start - | Reb 0 | Err 0"
    }

    private val liveHealthText = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 12f
        text = "State: IDLE\nElapsed: 0ms\nisPlaying: false\nCurrent bitrate (experimental): -"
    }

    private val sessionStatsText = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 12f
        text = ""
    }

    private val expandedCard = CardView(context).apply {
        radius = cardCornerRadiusPx
        cardElevation = cardElevationPx
        setCardBackgroundColor(Color.argb(232, 24, 24, 24))
        useCompatPadding = true
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                addView(
                    TextView(context).apply {
                        setTextColor(Color.WHITE)
                        textSize = 13f
                        text = "Live Health"
                    }
                )
                addView(liveHealthText)
                addView(
                    TextView(context).apply {
                        setTextColor(Color.WHITE)
                        textSize = 13f
                        text = "Session Stats"
                        setPadding(0, dp(8), 0, 0)
                    }
                )
                addView(sessionStatsText)
            },
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        )
    }

    private val handleView = TextView(context).apply {
        gravity = Gravity.CENTER
        text = "≡"
        textSize = 12f
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.argb(192, 0, 0, 0))
        setPadding(dp(4), dp(4), dp(4), dp(4))
        layoutParams = LinearLayout.LayoutParams(handleSizePx, handleSizePx).apply {
            marginStart = dp(6)
        }
        setOnClickListener {
            setOverlayState(if (currentState == OverlayState.COLLAPSED) OverlayState.EXPANDED else OverlayState.COLLAPSED)
        }
        setOnLongClickListener {
            dragging = true
            lastRawX = downRawX
            lastRawY = downRawY
            true
        }
        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    false
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!dragging) {
                        return@setOnTouchListener false
                    }
                    val dx = event.rawX - lastRawX
                    val dy = event.rawY - lastRawY
                    widgetContainer.translationX += dx
                    widgetContainer.translationY += dy
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!dragging) return@setOnTouchListener false
                    dragging = false
                    snapToNearestCorner()
                    true
                }

                else -> false
            }
        }
    }

    init {
        clipChildren = false
        clipToPadding = false
        isClickable = false
        isFocusable = false

        contentContainer.addView(
            collapsedText,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        )
        contentContainer.addView(
            expandedCard,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        )

        widgetContainer.addView(
            contentContainer,
            LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        )
        widgetContainer.addView(handleView)

        addView(
            widgetContainer,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        )

        setOverlayState(config.initialState)
        post { applyWidgetPosition(resetTranslation = true) }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean = false

    override fun onTouchEvent(event: MotionEvent?): Boolean = false

    fun updateInsets(left: Int, top: Int, right: Int, bottom: Int) {
        safeInsetLeft = left
        safeInsetTop = top
        safeInsetRight = right
        safeInsetBottom = bottom
        applyWidgetPosition(resetTranslation = true)
    }

    fun onSessionStarted(sessionId: String) {
        this.sessionId = sessionId
    }

    fun onSessionEnded(sessionId: String, finalSnapshot: SessionSnapshot) {
        this.sessionId = sessionId
        onSnapshotUpdated(finalSnapshot)
    }

    fun onSnapshotUpdated(snapshot: SessionSnapshot) {
        pendingSnapshot = snapshot
        if (updatePosted) return
        updatePosted = true
        post {
            updatePosted = false
            val latest = pendingSnapshot ?: return@post
            pendingSnapshot = null
            updateUi(latest)
        }
    }

    internal fun overlayStateForTest(): OverlayState = currentState

    internal fun pillTextForTest(): String = collapsedText.text.toString()

    internal fun pillColorForTest(): Int = currentPillColor

    internal fun performHandleClickForTest() {
        handleView.performClick()
    }

    private fun updateUi(snapshot: SessionSnapshot) {
        collapsedText.text = buildCollapsedText(snapshot)
        currentPillColor = resolveHealthColor(snapshot)
        pillBackground.setColor(currentPillColor)

        liveHealthText.text = buildLiveHealthText(snapshot)
        sessionStatsText.text = buildSessionStatsText(snapshot)
    }

    private fun buildCollapsedText(snapshot: SessionSnapshot): String {
        val startPart = snapshot.startupTimeMs?.let { "${it}ms" } ?: "-"
        return "▶ ${snapshot.playbackState} | Start $startPart | Reb ${snapshot.rebufferCount} | Err ${snapshot.errorCount}"
    }

    private fun buildLiveHealthText(snapshot: SessionSnapshot): String {
        val bitrate = snapshot.currentBitrate?.toString() ?: "-"
        return buildString {
            append("State: ${snapshot.playbackState}\n")
            append("Elapsed: ${snapshot.elapsedSessionTimeMs}ms\n")
            append("isPlaying: ${snapshot.isPlaying}\n")
            append("Current bitrate (experimental): $bitrate")
        }
    }

    private fun buildSessionStatsText(snapshot: SessionSnapshot): String {
        val meanBitrate = snapshot.meanVideoFormatBitrate?.toString() ?: "-"
        return buildString {
            append("sessionId: ${sessionId ?: snapshot.sessionId}\n")
            append("startupTimeMs: ${snapshot.startupTimeMs?.toString() ?: "-"}\n")
            append("rebufferTimeMs: ${snapshot.rebufferTimeMs}\n")
            append("rebufferCount: ${snapshot.rebufferCount}\n")
            append("playTimeMs: ${snapshot.playTimeMs}\n")
            append("rebufferRatio: ${String.format(Locale.US, "%.3f", snapshot.rebufferRatio)}\n")
            append("totalDroppedFrames: ${snapshot.totalDroppedFrames}\n")
            append("totalSeekCount: ${snapshot.totalSeekCount}\n")
            append("totalSeekTimeMs: ${snapshot.totalSeekTimeMs}\n")
            append("meanVideoFormatBitrate: $meanBitrate\n")
            append("errorCount: ${snapshot.errorCount}")
        }
    }

    private fun resolveHealthColor(snapshot: SessionSnapshot): Int {
        if (snapshot.errorCount > 0) return COLOR_HEALTH_RED
        if (snapshot.rebufferRatio > 0.05f) return COLOR_HEALTH_RED
        if (snapshot.rebufferRatio > 0.02f) return COLOR_HEALTH_YELLOW
        return COLOR_HEALTH_GREEN
    }

    private fun setOverlayState(state: OverlayState) {
        currentState = state
        collapsedText.visibility = if (state == OverlayState.COLLAPSED) VISIBLE else GONE
        expandedCard.visibility = if (state == OverlayState.EXPANDED) VISIBLE else GONE
    }

    private fun applyWidgetPosition(resetTranslation: Boolean) {
        val params = (widgetContainer.layoutParams as LayoutParams).apply {
            gravity = when (currentPosition) {
                OverlayPosition.TOP_START -> Gravity.TOP or Gravity.START
                OverlayPosition.TOP_END -> Gravity.TOP or Gravity.END
                OverlayPosition.BOTTOM_START -> Gravity.BOTTOM or Gravity.START
                OverlayPosition.BOTTOM_END -> Gravity.BOTTOM or Gravity.END
            }
            leftMargin = marginPx + safeInsetLeft
            topMargin = marginPx + safeInsetTop
            rightMargin = marginPx + safeInsetRight
            bottomMargin = marginPx + safeInsetBottom
        }
        widgetContainer.layoutParams = params
        if (resetTranslation) {
            widgetContainer.translationX = 0f
            widgetContainer.translationY = 0f
        }
    }

    private fun snapToNearestCorner() {
        if (width == 0 || height == 0 || widgetContainer.width == 0 || widgetContainer.height == 0) {
            post { snapToNearestCorner() }
            return
        }

        val currentX = widgetContainer.x
        val currentY = widgetContainer.y
        val targets = buildCornerTargets()
        val nearest = targets.minByOrNull { (_, point) ->
            distanceSquared(currentX, currentY, point.first, point.second)
        }?.key ?: currentPosition

        currentPosition = nearest
        rememberedPosition = nearest
        applyWidgetPosition(resetTranslation = true)
    }

    private fun buildCornerTargets(): Map<OverlayPosition, Pair<Float, Float>> {
        val topY = (marginPx + safeInsetTop).toFloat()
        val bottomY = (height - widgetContainer.height - marginPx - safeInsetBottom).toFloat()
        val leftX = (marginPx + safeInsetLeft).toFloat()
        val rightX = (width - widgetContainer.width - marginPx - safeInsetRight).toFloat()

        return mapOf(
            OverlayPosition.TOP_START to (leftX to topY),
            OverlayPosition.TOP_END to (rightX to topY),
            OverlayPosition.BOTTOM_START to (leftX to bottomY),
            OverlayPosition.BOTTOM_END to (rightX to bottomY),
        )
    }

    private fun distanceSquared(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return (x1 - x2).pow(2) + (y1 - y2).pow(2)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private companion object {
        const val COLOR_HEALTH_GREEN: Int = 0xFF2E7D32.toInt()
        const val COLOR_HEALTH_YELLOW: Int = 0xFFF9A825.toInt()
        const val COLOR_HEALTH_RED: Int = 0xFFC62828.toInt()
        var rememberedPosition: OverlayPosition? = null
    }
}
