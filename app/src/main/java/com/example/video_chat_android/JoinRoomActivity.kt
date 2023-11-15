package com.example.video_chat_android

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.transition.Slide
import android.transition.Transition
import android.transition.TransitionManager
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams

import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import pub.devrel.easypermissions.EasyPermissions.PermissionCallbacks
import kotlin.math.sqrt


class JoinRoomActivity : AppCompatActivity(), PermissionCallbacks {
    private val viewModel by viewModels<JoinRoomActivityViewModel>()
    private lateinit var publisherViewContainer: FrameLayout
    private lateinit var subscriberViewContainer: FrameLayout
    private lateinit var actionButtonViewContainer: FrameLayout
    private lateinit var videoMainViewContainer: FrameLayout
    private lateinit var liveCaptionButton: ImageButton
    private lateinit var endCallButton: ImageButton
    private lateinit var toggleMicButton: ImageButton
    private lateinit var toggleVideoButton: ImageButton
    private lateinit var switchCameraButton: ImageButton
    private lateinit var liveCaptionsText: TextView
    private var pressDownStartTime: Long = 0
    private var xDown: Float = 0F
    private var yDown: Float = 0F
    private var movedX: Float = 0F
    private var movedY: Float = 0F
    private var distanceX: Float = 0F
    private var distanceY: Float = 0F
    private var smallScreenX: Float = 0F
    private var smallScreenY: Float = 0F

    private val maxClickDuration = 1000
    private val maxClickDistance = 1
    private val smallScreenHeight = 330
    private val smallScreenWidth = 248

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_join_room)

        publisherViewContainer = findViewById(R.id.publisher_container)
        subscriberViewContainer = findViewById(R.id.subscriber_container)

        actionButtonViewContainer = findViewById(R.id.flActionButtons)
        videoMainViewContainer = findViewById(R.id.flVideoMainScreen)

        liveCaptionsText = findViewById(R.id.tvLiveCaption)

        endCallButton = findViewById(R.id.btEndCall)
        toggleMicButton = findViewById(R.id.btToggleMic)
        toggleVideoButton = findViewById(R.id.btToggleVideo)
        liveCaptionButton = findViewById(R.id.btLiveCaption)
        switchCameraButton = findViewById(R.id.btCycleCamera)

        // View model
        viewModel.isPubActive.observe(this) {
            if (it) {
                publisherViewContainer.addView(viewModel.publisher?.view)
                toggleActionButtonsVisibility()
                toggleLayout(viewModel.fullScreenView)
                updateMicUI()
                updateVideoUI()
            }
        }

        viewModel.isSubActive.observe(this) {
            if (it) {
                Log.i(TAG, "subscriber added")
                subscriberViewContainer.addView(viewModel.subscriber?.view)
            }
        }


        viewModel.liveCaptions.observe(this) {
            liveCaptionsText.text = it
        }

        if(viewModel.session == null) {
            requestPermissions()
        }


        // Action Button
        endCallButton.setOnClickListener {
            disconnectSession()
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
        toggleMicButton.setOnClickListener {
            toggleMic()
        }
        toggleVideoButton.setOnClickListener {
            toggleVideo()
        }
        switchCameraButton.setOnClickListener {
            switchCamera()
        }
        liveCaptionButton.setOnClickListener {
            toggleLiveCaption()
        }

        publisherViewContainer.setOnTouchListener { _, motionEvent ->
            moveContainer(motionEvent, "publisher")
        }
        subscriberViewContainer.setOnTouchListener { _, motionEvent ->
            moveContainer(motionEvent, "subscriber")
        }
    }

    private fun moveContainer(motionEvent:MotionEvent, targetView: String): Boolean {
        when (motionEvent.action) {
            MotionEvent.ACTION_DOWN -> {
                pressDownStartTime = System.currentTimeMillis()
                xDown = motionEvent.x
                yDown = motionEvent.y
                distanceX = 0F
                distanceY = 0F
            }
            MotionEvent.ACTION_MOVE -> {
                if (targetView === "publisher" && publisherViewContainer.height != smallScreenHeight ||
                    targetView === "subscriber" && subscriberViewContainer.height != smallScreenHeight)
                {return true}

                 movedX = motionEvent.x
                 movedY = motionEvent.y
                 distanceX = movedX - xDown
                 distanceY = movedY - yDown

                if (targetView === "publisher") {
                    smallScreenX = publisherViewContainer.x + distanceX
                    smallScreenY = publisherViewContainer.y + distanceY
                    publisherViewContainer.x = smallScreenX
                    publisherViewContainer.y = smallScreenY
                }
                else if (targetView === "subscriber") {
                    smallScreenX = subscriberViewContainer.x + distanceX
                    smallScreenY = subscriberViewContainer.y + distanceY
                    subscriberViewContainer.x = smallScreenX
                    subscriberViewContainer.y = smallScreenY
                }
            }
            MotionEvent.ACTION_UP -> {
                if (targetView === "publisher" && publisherViewContainer.height != smallScreenHeight ||
                    targetView === "subscriber" && subscriberViewContainer.height != smallScreenHeight) {
                    toggleActionButtonsVisibility()
                    return true
                }

                // Ensure small screen stays at left/right corner and within main screen
                if (targetView === "publisher") {
                    smallScreenX = if ((publisherViewContainer.x + smallScreenWidth/2) < videoMainViewContainer.width/2) {
                        0F
                    } else {
                        (videoMainViewContainer.width - smallScreenWidth).toFloat()
                    }
                    if (publisherViewContainer.y < 0) {
                        smallScreenY= 0F
                    }
                    else if (publisherViewContainer.y > (videoMainViewContainer.height - smallScreenHeight)) {
                        smallScreenY= (videoMainViewContainer.height - smallScreenHeight).toFloat()
                    }
                    publisherViewContainer.x = smallScreenX
                    publisherViewContainer.y = smallScreenY
                }
                else if (targetView === "subscriber") {
                    smallScreenX = if ((subscriberViewContainer.x + smallScreenWidth/2) < videoMainViewContainer.width/2) {
                        0F
                    } else {
                        (videoMainViewContainer.width - smallScreenWidth).toFloat()
                    }
                    if (subscriberViewContainer.y < 0) {
                        smallScreenY= 0F
                    }
                    else if (subscriberViewContainer.y > (videoMainViewContainer.height - smallScreenHeight)) {
                        smallScreenY= (videoMainViewContainer.height - smallScreenHeight).toFloat()
                    }
                    subscriberViewContainer.x = smallScreenX
                    subscriberViewContainer.y = smallScreenY
                }
                val pressDuration: Long = System.currentTimeMillis() - pressDownStartTime
                val distanceInPx = sqrt((distanceX * distanceX + distanceY*distanceY).toDouble()).toFloat()
                val distance = distanceInPx / resources.displayMetrics.density

                if (pressDuration < maxClickDuration &&  distance < maxClickDistance) {
                    toggleLayout(targetView)
                }

            }
        }
        return true

    }
    private fun toggleActionButtonsVisibility() {
        val transition: Transition = Slide(Gravity.BOTTOM)
        transition.addTarget(actionButtonViewContainer)
        TransitionManager.beginDelayedTransition(actionButtonViewContainer, transition)
        if (actionButtonViewContainer.visibility == View.VISIBLE) {
            actionButtonViewContainer.visibility = View.GONE
        }
        else {
            actionButtonViewContainer.visibility = View.VISIBLE
        }
    }
    private fun toggleLayout(targetView: String) {
        viewModel.fullScreenView = targetView
        if (targetView === "publisher") {
            publisherViewContainer.updateLayoutParams {
                height = ViewGroup.LayoutParams.MATCH_PARENT
                width = ViewGroup.LayoutParams.MATCH_PARENT
            }
            publisherViewContainer.x = 0F
            publisherViewContainer.y = 0F

            subscriberViewContainer.updateLayoutParams {
                height =  smallScreenHeight
                width = smallScreenWidth
            }
            subscriberViewContainer.x = smallScreenX
            subscriberViewContainer.y = smallScreenY
            subscriberViewContainer.bringToFront()
        }
        else {
            publisherViewContainer.updateLayoutParams {
                height = smallScreenHeight
                width = smallScreenWidth
            }
            publisherViewContainer.x = smallScreenX
            publisherViewContainer.y = smallScreenY

            subscriberViewContainer.updateLayoutParams {
                height =  ViewGroup.LayoutParams.MATCH_PARENT
                width = ViewGroup.LayoutParams.MATCH_PARENT

            }
            subscriberViewContainer.x = 0F
            subscriberViewContainer.y = 0F

            publisherViewContainer.bringToFront()
        }
    }
    private fun toggleMic() {
        viewModel.publisher?.publishAudio = viewModel.publisher?.publishAudio != true
        updateMicUI()
    }

    private fun toggleVideo() {
        viewModel.publisher?.publishVideo = viewModel.publisher?.publishVideo != true
        updateVideoUI()
    }

    private fun toggleLiveCaption() {
        viewModel.subscriber?.subscribeToCaptions = viewModel.subscriber?.subscribeToCaptions != true
        updateLiveCaptionUI()
    }

    private fun updateMicUI() {
        if (viewModel.publisher?.publishAudio == true) {
            toggleMicButton.setBackgroundResource(R.drawable.greenroundcorner_bg)
            toggleMicButton.setImageResource(R.drawable.ic_mic_on)
        }
        else {
            toggleMicButton.setBackgroundResource(R.drawable.redroundcorner_bg)
            toggleMicButton.setImageResource(R.drawable.ic_mic_off)
        }
    }

    private fun updateVideoUI() {
        if (viewModel.publisher?.publishVideo == true) {
            toggleVideoButton.setBackgroundResource(R.drawable.greenroundcorner_bg)
            toggleVideoButton.setImageResource(R.drawable.ic_video_on)
        }
        else {
            toggleVideoButton.setBackgroundResource(R.drawable.redroundcorner_bg)
            toggleVideoButton.setImageResource(R.drawable.ic_video_off)
        }
    }

    private fun updateLiveCaptionUI() {
        if (viewModel.subscriber?.subscribeToCaptions == true) {
            liveCaptionButton.setBackgroundResource(R.drawable.greenroundcorner_bg)
            liveCaptionButton.setImageResource(R.drawable.ic_closed_caption_42)
            liveCaptionsText.visibility = View.VISIBLE
        }
        else {
            liveCaptionButton.setBackgroundResource(R.drawable.redroundcorner_bg)
            liveCaptionButton.setImageResource(R.drawable.ic_closed_caption_disabled_42)
            liveCaptionsText.visibility = View.GONE
        }
    }

    private fun switchCamera() {
        viewModel.publisher?.cycleCamera()
    }


    override fun onDestroy() {
        super.onDestroy()
        publisherViewContainer.removeAllViews()
        subscriberViewContainer.removeAllViews()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onPermissionsGranted(requestCode: Int, perms: List<String>) {
        Log.i(TAG, "onPermissionsGranted:$requestCode: $perms")
    }

    override fun onPermissionsDenied(requestCode: Int, perms: List<String>) {
        finishWithMessage("onPermissionsDenied: $requestCode: $perms")
    }

    @AfterPermissionGranted(PERMISSIONS_REQUEST_CODE)
    private fun requestPermissions() {
        val perms = arrayOf(Manifest.permission.INTERNET, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (EasyPermissions.hasPermissions(this, *perms)) {
            if (ServerConfig.hasChatServerUrl()) {
                // Custom server URL exists - retrieve session config
                if (!ServerConfig.isValid) {
                    finishWithMessage("Invalid chat server url: ${ServerConfig.CHAT_SERVER_URL}")
                    return
                }
                viewModel.initRetrofit()
                val roomName = intent.getStringExtra("roomName")
                if (roomName != null)
                    viewModel.getSession(roomName)
            } else {
                finishWithMessage("Invalid Server Url")
                return
            }
        } else {
            EasyPermissions.requestPermissions(
                this,
                getString(R.string.rationale_video_app),
                PERMISSIONS_REQUEST_CODE,
                *perms
            )
        }
    }


    private fun disconnectSession() {
        if (viewModel.session == null) {
            return
        }

        if (viewModel.subscriber != null) {
            subscriberViewContainer.removeView(viewModel.subscriber?.view)
            viewModel.session?.unsubscribe(viewModel.subscriber)
            viewModel.subscriber = null
        }

        if (viewModel.publisher != null) {
            publisherViewContainer.removeView(viewModel.publisher?.view)
            viewModel.session?.unpublish(viewModel.publisher)
            viewModel.publisher = null
        }
        viewModel.session?.disconnect()
    }

    private fun finishWithMessage(message: String) {
        Log.e(TAG, message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
        private const val PERMISSIONS_REQUEST_CODE = 124
    }
}
