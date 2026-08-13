package com.geneo.smartboard.overlay

import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    companion object {
        private const val CHANNEL_ID = "geneo_toolbox_channel"
        private const val NOTIF_ID = 1001

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var inflater: LayoutInflater
    private var touchSlop = 16

    // Bubble
    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var isDockedRight = true

    // Menu
    private var menuView: View? = null
    private var menuParams: WindowManager.LayoutParams? = null
    private var isMenuOpen = false
    private var isAnimatingMenu = false

    // Tool windows
    private var stopwatchView: View? = null
    private var stopwatchController: StopwatchController? = null
    private var timerView: View? = null
    private var timerController: TimerController? = null
    private var calculatorView: View? = null
    private var calculatorController: CalculatorController? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        inflater = LayoutInflater.from(this)
        touchSlop = android.view.ViewConfiguration.get(this).scaledTouchSlop
        createNotificationChannel()
        addBubble()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        removeMenu(animate = false)
        closeStopwatch()
        closeTimer()
        closeCalculator()
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        bubbleView = null
    }

    // ---------------------------------------------------------------------
    // Notification
    // ---------------------------------------------------------------------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_MIN
            )
            channel.setShowBadge(false)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE
        } else 0
        val pendingIntent = PendingIntent.getActivity(this, 0, openAppIntent, pendingFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bubble_grid)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    // ---------------------------------------------------------------------
    // Bubble
    // ---------------------------------------------------------------------

    private fun overlayWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun screenSize(): Pair<Int, Int> {
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(dm)
        return dm.widthPixels to dm.heightPixels
    }

    private fun addBubble() {
        if (bubbleView != null) return
        val view = inflater.inflate(R.layout.overlay_bubble, null)
        val (screenW, screenH) = screenSize()
        val bubbleSizePx = dp(56)

        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = screenW - bubbleSizePx - dp(8)
        params.y = (screenH * 0.35f).toInt()

        val dragHelper = DragHelper(
            windowManager = windowManager,
            targetView = view,
            params = params,
            touchSlopPx = touchSlop,
            onTap = { toggleMenu() },
            onDragStart = {
                if (isMenuOpen) removeMenu(animate = false)
            },
            onDragEnd = { finalX, _ ->
                snapToEdge(view, params, finalX)
            }
        )
        view.setOnTouchListener(dragHelper)

        windowManager.addView(view, params)
        bubbleView = view
        bubbleParams = params
        isDockedRight = params.x + bubbleSizePx / 2 > screenW / 2
    }

    private fun snapToEdge(view: View, params: WindowManager.LayoutParams, currentX: Int) {
        val (screenW, _) = screenSize()
        val bubbleSizePx = dp(56)
        val margin = dp(8)
        val targetX = if (currentX + bubbleSizePx / 2 < screenW / 2) {
            margin
        } else {
            screenW - bubbleSizePx - margin
        }
        isDockedRight = targetX != margin

        val animator = ValueAnimator.ofInt(currentX, targetX)
        animator.duration = 220
        animator.interpolator = android.view.animation.DecelerateInterpolator()
        animator.addUpdateListener {
            params.x = it.animatedValue as Int
            runCatching { windowManager.updateViewLayout(view, params) }
        }
        animator.start()
    }

    // ---------------------------------------------------------------------
    // Menu (stopwatch / timer / calculator picker)
    // ---------------------------------------------------------------------

    private fun toggleMenu() {
        if (isAnimatingMenu) return
        if (isMenuOpen) {
            removeMenu(animate = true)
        } else {
            showMenu()
        }
    }

    private fun showMenu() {
        val bubble = bubbleView ?: return
        val bParams = bubbleParams ?: return
        if (menuView != null) return

        val view = inflater.inflate(R.layout.overlay_menu, null)
        view.layoutDirection = if (isDockedRight) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL
        view.alpha = 1f

        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = bParams.x
        params.y = bParams.y

        // Prime the 3 rows as invisible/scaled-down; they'll animate in once positioned.
        val items = listOf(
            view.findViewById<View>(R.id.itemCalculator),
            view.findViewById<View>(R.id.itemTimer),
            view.findViewById<View>(R.id.itemStopwatch)
        )
        items.forEach {
            it.alpha = 0f
            it.scaleX = 0.6f
            it.scaleY = 0.6f
            it.translationY = dp(16).toFloat()
        }

        view.findViewById<View>(R.id.btnCalculator).setOnClickListener { onToolChosen { openCalculator() } }
        view.findViewById<View>(R.id.btnTimer).setOnClickListener { onToolChosen { openTimer() } }
        view.findViewById<View>(R.id.btnStopwatch).setOnClickListener { onToolChosen { openStopwatch() } }

        windowManager.addView(view, params)
        menuView = view
        menuParams = params
        isMenuOpen = true
        isAnimatingMenu = true

        // Wait for a real measurement pass, then reposition flush against the
        // bubble and play the staggered entrance animation.
        view.post {
            val (screenW, screenH) = screenSize()
            val bubbleSizePx = dp(56)
            val menuW = view.width
            val menuH = view.height

            params.x = if (isDockedRight) {
                (bParams.x + bubbleSizePx) - menuW
            } else {
                bParams.x
            }.coerceIn(0, (screenW - menuW).coerceAtLeast(0))

            val spaceAbove = bParams.y
            params.y = if (spaceAbove >= menuH + dp(12)) {
                bParams.y - menuH - dp(8)
            } else {
                bParams.y + bubbleSizePx + dp(8)
            }.coerceIn(0, (screenH - menuH).coerceAtLeast(0))

            runCatching { windowManager.updateViewLayout(view, params) }

            val overshoot = OvershootInterpolator(1.6f)
            items.forEachIndexed { index, item ->
                item.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setStartDelay(index * 55L)
                    .setDuration(220)
                    .setInterpolator(overshoot)
                    .withEndAction { if (index == items.lastIndex) isAnimatingMenu = false }
                    .start()
            }
        }
    }

    private fun onToolChosen(action: () -> Unit) {
        removeMenu(animate = true)
        action()
    }

    private fun removeMenu(animate: Boolean) {
        val view = menuView ?: run { isMenuOpen = false; return }
        isMenuOpen = false

        if (!animate) {
            runCatching { windowManager.removeView(view) }
            menuView = null
            menuParams = null
            isAnimatingMenu = false
            return
        }

        isAnimatingMenu = true
        val items = listOf(
            view.findViewById<View>(R.id.itemStopwatch),
            view.findViewById<View>(R.id.itemTimer),
            view.findViewById<View>(R.id.itemCalculator)
        )
        items.forEachIndexed { index, item ->
            item.animate()
                .alpha(0f)
                .scaleX(0.6f)
                .scaleY(0.6f)
                .translationY(dp(16).toFloat())
                .setStartDelay(index * 40L)
                .setDuration(150)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction {
                    if (index == items.lastIndex) {
                        runCatching { windowManager.removeView(view) }
                        if (menuView === view) {
                            menuView = null
                            menuParams = null
                        }
                        isAnimatingMenu = false
                    }
                }
                .start()
        }
    }

    // ---------------------------------------------------------------------
    // Tool windows
    // ---------------------------------------------------------------------

    private fun addToolWindow(view: View, headerId: Int) {
        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER
        params.x = 0
        params.y = 0

        view.alpha = 0f
        view.scaleX = 0.9f
        view.scaleY = 0.9f

        windowManager.addView(view, params)

        val header = view.findViewById<View>(headerId)
        val dragHelper = DragHelper(
            windowManager = windowManager,
            targetView = view,
            params = params,
            touchSlopPx = touchSlop,
            onTap = { /* tapping the header does nothing; drag to move, X to close */ }
        )
        header.setOnTouchListener(dragHelper)

        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(180)
            .setInterpolator(OvershootInterpolator(1.2f))
            .start()
    }

    private fun openStopwatch() {
        if (stopwatchView != null) return
        val view = inflater.inflate(R.layout.overlay_stopwatch, null)
        addToolWindow(view, R.id.stopwatchHeader)
        stopwatchController = StopwatchController(view)
        view.findViewById<View>(R.id.btnCloseStopwatch).setOnClickListener { closeStopwatch() }
        stopwatchView = view
    }

    private fun closeStopwatch() {
        val view = stopwatchView ?: return
        stopwatchController?.stop()
        stopwatchController = null
        stopwatchView = null
        runCatching { windowManager.removeView(view) }
    }

    private fun openTimer() {
        if (timerView != null) return
        val view = inflater.inflate(R.layout.overlay_timer, null)
        addToolWindow(view, R.id.timerHeader)
        timerController = TimerController(view)
        view.findViewById<View>(R.id.btnCloseTimer).setOnClickListener { closeTimer() }
        timerView = view
    }

    private fun closeTimer() {
        val view = timerView ?: return
        timerController?.stop()
        timerController = null
        timerView = null
        runCatching { windowManager.removeView(view) }
    }

    private fun openCalculator() {
        if (calculatorView != null) return
        val view = inflater.inflate(R.layout.overlay_calculator, null)
        addToolWindow(view, R.id.calculatorHeader)
        calculatorController = CalculatorController(view)
        view.findViewById<View>(R.id.btnCloseCalculator).setOnClickListener { closeCalculator() }
        calculatorView = view
    }

    private fun closeCalculator() {
        val view = calculatorView ?: return
        calculatorController = null
        calculatorView = null
        runCatching { windowManager.removeView(view) }
    }

    // ---------------------------------------------------------------------
    // Utils
    // ---------------------------------------------------------------------

    private fun dp(value: Int): Int {
        val density = resources.displayMetrics.density
        return (value * density).toInt()
    }
}
