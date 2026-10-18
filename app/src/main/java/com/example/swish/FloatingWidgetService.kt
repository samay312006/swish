package com.example.swish
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.ViewConfiguration


class FloatingWidgetService : Service() {


    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        // 1. Create a silent notification to keep the service alive
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "SWISH_CHANNEL",
                "Swish Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }



        // 1. Inflate your custom layout (e.g., a simple layout with an ImageView/Button)
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_widget, null)

        // 2. Configure the overlay window parameters
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, // Required for modern Android
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        // ... inside onCreate() ...
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_widget, null)

        // Put the widget on the top left by default
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100

        // 3. Add the view to the window
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager.addView(floatingView, params)

        // ... inside onCreate() ...

        val captureButton = floatingView.findViewById<View>(R.id.fab_capture)

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isMoved = false

        // Holding the bubble still opens the "Heart contact" setting.
        val longPressHandler = Handler(Looper.getMainLooper())
        var isLongPressed = false
        val openHeartContact = Runnable {
            isLongPressed = true
            captureButton.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            val intent = Intent(this, HeartContactActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }

        captureButton.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMoved = false
                    isLongPressed = false
                    longPressHandler.postDelayed(openHeartContact, ViewConfiguration.getLongPressTimeout().toLong())
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()

                    // Only move if they dragged their finger a decent amount
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isMoved = true
                        longPressHandler.removeCallbacks(openHeartContact)
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager.updateViewLayout(floatingView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    longPressHandler.removeCallbacks(openHeartContact)
                    // If they lifted their finger without dragging (or long-pressing), trigger a click!
                    if (!isMoved && !isLongPressed) {
                        view.performClick()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    longPressHandler.removeCallbacks(openHeartContact)
                    true
                }
                else -> false
            }
        }

        // What happens when it is clicked (opens the whiteboard)
        captureButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val notification = NotificationCompat.Builder(this, "SWISH_CHANNEL")
            .setContentTitle("Swish is Active")
            .setContentText("Waiting for gestures...")
            .setSmallIcon(android.R.drawable.ic_menu_camera) // System icon for safety
            .build()


        if (Build.VERSION.SDK_INT >= 34) { // Android 14+
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
        // This tells Android: "If you kill this service, restart it immediately!"
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
}