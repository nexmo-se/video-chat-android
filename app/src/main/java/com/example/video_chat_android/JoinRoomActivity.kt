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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import com.example.video_chat_android.network.APIService
import com.example.video_chat_android.network.GetSessionResponse
import com.opentok.android.*
import com.opentok.android.PublisherKit.PublisherListener
import com.opentok.android.Session.SessionListener
import com.opentok.android.SubscriberKit.SubscriberListener
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import pub.devrel.easypermissions.EasyPermissions.PermissionCallbacks
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory


class JoinRoomActivity : AppCompatActivity(), PermissionCallbacks {
    private var retrofit: Retrofit? = null
    private var apiService: APIService? = null
    private var session: Session? = null
    private var publisher: Publisher? = null
    private var subscriber: Subscriber? = null
    private lateinit var publisherViewContainer: FrameLayout
    private lateinit var subscriberViewContainer: FrameLayout
    private lateinit var actionButtonViewContainer: FrameLayout
    private lateinit var videoMainViewContrainer: FrameLayout
    private lateinit var endCallButton: ImageButton;
    private lateinit var toggleMicButton: ImageButton;
    private lateinit var toggleVideoButton: ImageButton;
    private lateinit var switchCameraButton: ImageButton;
    private var pressDownStartTime: Long = 0;
    private var xDown: Float = 0F;
    private var yDown: Float = 0F;
    private var movedX: Float = 0F;
    private var movedY: Float = 0F;
    private var distanceX: Float = 0F;
    private var distanceY: Float = 0F;
    private var smallScreenX: Float = 0F;
    private var smallScreenY: Float = 0F;

    private val MAX_CLICK_DURATION = 1000
    private val MAX_CLICK_DISTANCE = 1
    private val SMALL_SCREEN_HEIGHT = 330
    private val SMALL_SCREEN_WIDTH = 248



    private val publisherListener: PublisherListener = object : PublisherListener {
        override fun onStreamCreated(publisherKit: PublisherKit, stream: Stream) {
            Log.d(TAG, "onStreamCreated: Publisher Stream Created. Own stream ${stream.streamId}")
        }

        override fun onStreamDestroyed(publisherKit: PublisherKit, stream: Stream) {
            Log.d(TAG, "onStreamDestroyed: Publisher Stream Destroyed. Own stream ${stream.streamId}")
        }

        override fun onError(publisherKit: PublisherKit, opentokError: OpentokError) {
            finishWithMessage("PublisherKit onError: ${opentokError.message}")
        }
    }
    private val sessionListener: SessionListener = object : SessionListener {
        override fun onConnected(session: Session) {
            Log.d(TAG, "onConnected: Connected to session: ${session.sessionId}")
           publisher = Publisher.Builder(this@JoinRoomActivity).build()

            publisher?.setPublisherListener(publisherListener)
            publisher?.renderer?.setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE, BaseVideoRenderer.STYLE_VIDEO_FILL)
            publisherViewContainer.addView(publisher?.view)
            publisherViewContainer.bringToFront();

            session.publish(publisher)
            toggleActionButtonsVisibility();
        }

        override fun onDisconnected(session: Session) {
            Log.d(TAG, "onDisconnected: Disconnected from session: ${session.sessionId}")
        }

        override fun onStreamReceived(session: Session, stream: Stream) {
            Log.d(TAG, "onStreamReceived: New Stream Received ${stream.streamId} in session: ${session.sessionId}")
            if (subscriber == null) {
                subscriber = Subscriber.Builder(this@JoinRoomActivity, stream).build().also {
                    it.renderer?.setStyle(
                        BaseVideoRenderer.STYLE_VIDEO_SCALE,
                        BaseVideoRenderer.STYLE_VIDEO_FILL
                    )

                    it.setSubscriberListener(subscriberListener)
                }

                session.subscribe(subscriber)
                subscriberViewContainer.addView(subscriber?.view)
            }
        }

        override fun onStreamDropped(session: Session, stream: Stream) {
            Log.d(TAG, "onStreamDropped: Stream Dropped: ${stream.streamId} in session: ${session.sessionId}")
            if (subscriber != null) {
                subscriber = null
                subscriberViewContainer.removeAllViews()
            }
        }

        override fun onError(session: Session, opentokError: OpentokError) {
            finishWithMessage("Session error: ${opentokError.message}")
        }
    }
    var subscriberListener: SubscriberListener = object : SubscriberListener {
        override fun onConnected(subscriberKit: SubscriberKit) {
            Log.d(TAG, "onConnected: Subscriber connected. Stream: ${subscriberKit.stream.streamId}")
        }

        override fun onDisconnected(subscriberKit: SubscriberKit) {
            Log.d(TAG, "onDisconnected: Subscriber disconnected. Stream: ${subscriberKit.stream.streamId}")
        }

        override fun onError(subscriberKit: SubscriberKit, opentokError: OpentokError) {
            finishWithMessage("SubscriberKit onError: ${opentokError.message}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_join_room)

        publisherViewContainer = findViewById(R.id.publisher_container)
        subscriberViewContainer = findViewById(R.id.subscriber_container)
        requestPermissions()

        actionButtonViewContainer = findViewById(R.id.flActionButtons);
        videoMainViewContrainer = findViewById(R.id.flVideoMainScreen);

        endCallButton = findViewById<ImageButton>(R.id.btEndCall);
        toggleMicButton = findViewById<ImageButton>(R.id.btToggleMic);
        toggleVideoButton = findViewById<ImageButton>(R.id.btToggleVideo);
        switchCameraButton = findViewById<ImageButton>(R.id.btCycleCamera);
        endCallButton.setOnClickListener {
            disconnectSession();
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
        toggleMicButton.setOnClickListener {
            toggleMic();
        }
        toggleVideoButton.setOnClickListener {
            toggleVideo()
        }
        switchCameraButton.setOnClickListener {
            switchCamera();
        }

        publisherViewContainer.setOnTouchListener { _, motionEvent ->
            moveContainer(motionEvent, "publisher");
        }
        subscriberViewContainer.setOnTouchListener { _, motionEvent ->
            moveContainer(motionEvent, "subscriber");
        }
    }
    private fun moveContainer(motionEvent:MotionEvent, targetView: String): Boolean {
        when (motionEvent.action) {
            MotionEvent.ACTION_DOWN -> {
                pressDownStartTime = System.currentTimeMillis()
                xDown = motionEvent.getX();
                yDown = motionEvent.getY();
                distanceX = 0F;
                distanceY = 0F;
            }
            MotionEvent.ACTION_MOVE -> {
                if (targetView === "publisher" && publisherViewContainer.height !== SMALL_SCREEN_HEIGHT ||
                    targetView === "subscriber" && subscriberViewContainer.height !== SMALL_SCREEN_HEIGHT)
                {return true}

                 movedX = motionEvent.getX();
                 movedY = motionEvent.getY();
                 distanceX = movedX - xDown;
                 distanceY = movedY - yDown;

                if (targetView === "publisher") {
                    smallScreenX = publisherViewContainer.x + distanceX;
                    smallScreenY = publisherViewContainer.y + distanceY;
                    publisherViewContainer.setX(smallScreenX);
                    publisherViewContainer.setY(smallScreenY);
                }
                else if (targetView === "subscriber") {
                    smallScreenX = subscriberViewContainer.x + distanceX;
                    smallScreenY = subscriberViewContainer.y + distanceY;
                    subscriberViewContainer.setX(smallScreenX);
                    subscriberViewContainer.setY(smallScreenY);
                }
            }
            MotionEvent.ACTION_UP -> {
                if (targetView === "publisher" && publisherViewContainer.height !== SMALL_SCREEN_HEIGHT ||
                    targetView === "subscriber" && subscriberViewContainer.height !== SMALL_SCREEN_HEIGHT) {
                    toggleActionButtonsVisibility();
                    return true
                }

                // Ensure small screen stays at left/right corner and within main screen
                if (targetView === "publisher") {
                    if ((publisherViewContainer.x + SMALL_SCREEN_WIDTH/2) < videoMainViewContrainer.width/2) {
                        smallScreenX=0F
                    }
                    else {
                        smallScreenX= (videoMainViewContrainer.width - SMALL_SCREEN_WIDTH).toFloat()
                    }
                    if (publisherViewContainer.y < 0) {
                        smallScreenY= 0F
                    }
                    else if (publisherViewContainer.y > (videoMainViewContrainer.height - SMALL_SCREEN_HEIGHT)) {
                        smallScreenY= (videoMainViewContrainer.height - SMALL_SCREEN_HEIGHT).toFloat()
                    }
                    publisherViewContainer.setX(smallScreenX)
                    publisherViewContainer.setY(smallScreenY)
                }
                else if (targetView === "subscriber") {
                    if ((subscriberViewContainer.x + SMALL_SCREEN_WIDTH/2) < videoMainViewContrainer.width/2) {
                        smallScreenX=0F
                    }
                    else {
                        smallScreenX= (videoMainViewContrainer.width - SMALL_SCREEN_WIDTH).toFloat()
                    }
                    if (subscriberViewContainer.y < 0) {
                        smallScreenY= 0F
                    }
                    else if (subscriberViewContainer.y > (videoMainViewContrainer.height - SMALL_SCREEN_HEIGHT)) {
                        smallScreenY= (videoMainViewContrainer.height - SMALL_SCREEN_HEIGHT).toFloat()
                    }
                    subscriberViewContainer.setX(smallScreenX)
                    subscriberViewContainer.setY(smallScreenY)
                }
                val pressDuration: Long = System.currentTimeMillis() - pressDownStartTime
                val distanceInPx = Math.sqrt((distanceX * distanceX + distanceY*distanceY).toDouble()).toFloat()
                val distance = distanceInPx / getResources().getDisplayMetrics().density;

                if (pressDuration < MAX_CLICK_DURATION &&  distance < MAX_CLICK_DISTANCE) {
                    toggleLayout(targetView);
                }

            }
        }
        return true

    }
    private fun toggleActionButtonsVisibility() {
        val transition: Transition = Slide(Gravity.BOTTOM)
        transition.addTarget(actionButtonViewContainer);
        TransitionManager.beginDelayedTransition(actionButtonViewContainer, transition);
        if (actionButtonViewContainer.getVisibility() === View.VISIBLE) {
            actionButtonViewContainer.setVisibility(View.INVISIBLE)
        }
        else {
            actionButtonViewContainer.setVisibility(View.VISIBLE)
        }
    }
    private fun toggleLayout(targetView: String) {

        if (targetView === "publisher") {
            publisherViewContainer.updateLayoutParams {
                height = ViewGroup.LayoutParams.MATCH_PARENT
                width = ViewGroup.LayoutParams.MATCH_PARENT
            }
            publisherViewContainer.setX(0F);
            publisherViewContainer.setY(0F);

            subscriberViewContainer.updateLayoutParams {
                height =  SMALL_SCREEN_HEIGHT
                width = SMALL_SCREEN_WIDTH
            }
            subscriberViewContainer.setX(smallScreenX);
            subscriberViewContainer.setY(smallScreenY);
            subscriberViewContainer.bringToFront();
        }
        else {
            publisherViewContainer.updateLayoutParams {
                height = SMALL_SCREEN_HEIGHT
                width = SMALL_SCREEN_WIDTH
            }
            publisherViewContainer.setX(smallScreenX);
            publisherViewContainer.setY(smallScreenY);

            subscriberViewContainer.updateLayoutParams {
                height =  ViewGroup.LayoutParams.MATCH_PARENT
                width = ViewGroup.LayoutParams.MATCH_PARENT

            }
            subscriberViewContainer.setX(0F);
            subscriberViewContainer.setY(0F);

            publisherViewContainer.bringToFront();
        }
    }
    private fun toggleMic() {
        if (publisher?.publishAudio == true) {
            publisher?.publishAudio = false;
            toggleMicButton.setBackgroundResource(R.drawable.redroundcorner_bg);
            toggleMicButton.setImageResource(R.drawable.ic_mic_off)
        }
        else {
            publisher?.publishAudio = true;
            toggleMicButton.setBackgroundResource(R.drawable.greenroundcorner_bg);
            toggleMicButton.setImageResource(R.drawable.ic_mic_on)
        }
    }
    private fun toggleVideo() {
        if (publisher?.publishVideo == true) {
            publisher?.publishVideo = false;
            toggleVideoButton.setBackgroundResource(R.drawable.redroundcorner_bg);
            toggleVideoButton.setImageResource(R.drawable.ic_video_off)
        }
        else {
            publisher?.publishVideo = true;
            toggleVideoButton.setBackgroundResource(R.drawable.greenroundcorner_bg);
            toggleVideoButton.setImageResource(R.drawable.ic_video_on)
        }
    }
    private fun switchCamera() {
        publisher?.cycleCamera();
    }

    override fun onPause() {
        super.onPause()
        if (session == null) {
            return
        }
        session?.onPause()
        if (isFinishing) {
            disconnectSession()
        }
    }

    override fun onResume() {
        super.onResume()
        if (session == null) {
            return
        }
        session?.onResume()
    }

    override fun onDestroy() {
        disconnectSession()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onPermissionsGranted(requestCode: Int, perms: List<String>) {
        Log.d(TAG, "onPermissionsGranted:$requestCode: $perms")
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
                initRetrofit()
                getSession()
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

    /* Make a request for session data */
    private fun getSession() {
        Log.i(TAG, "getSession")

        val roomName = intent.getStringExtra("roomName")
        val requestCall = apiService?.getCredential(roomName)

        requestCall?.enqueue(object : Callback<GetSessionResponse?> {
            override fun onResponse(call: Call<GetSessionResponse?>, response: Response<GetSessionResponse?>) {
                response.body()?.also {
                    initializeSession(it.apiKey, it.sessionId, it.token)
                }
            }

            override fun onFailure(call: Call<GetSessionResponse?>, t: Throwable) {
                throw RuntimeException(t.message)
            }
        })
    }

    private fun initializeSession(apiKey: String, sessionId: String, token: String) {
        Log.i(TAG, "apiKey: $apiKey")
        Log.i(TAG, "sessionId: $sessionId")
        Log.i(TAG, "token: $token")

        /*
        The context used depends on the specific use case, but usually, it is desired for the session to
        live outside of the Activity e.g: live between activities. For a production applications,
        it's convenient to use Application context instead of Activity context.
         */
        session = Session.Builder(this, apiKey, sessionId)
            .sessionOptions(object : Session.SessionOptions() {
                override fun useTextureViews(): Boolean {
                    return true
                }
            }).build().also {
            it.setSessionListener(sessionListener)
            it.connect(token)
        }
    }

    private fun initRetrofit() {
        val logging = HttpLoggingInterceptor()
        logging.setLevel(HttpLoggingInterceptor.Level.BODY)
        val client: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        retrofit = Retrofit.Builder()
            .baseUrl(ServerConfig.CHAT_SERVER_URL)
            .addConverterFactory(MoshiConverterFactory.create())
            .client(client)
            .build().also {
                apiService = it.create(APIService::class.java)
            }
    }

    private fun disconnectSession() {
        if (session == null) {
            return
        }

        if (subscriber != null) {
            subscriberViewContainer.removeView(subscriber?.view)
            session?.unsubscribe(subscriber)
            subscriber = null
        }

        if (publisher != null) {
            publisherViewContainer.removeView(publisher?.view)
            session?.unpublish(publisher)
            publisher = null
        }
        session?.disconnect()
    }

    private fun finishWithMessage(message: String) {
        Log.e(TAG, message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
        private const val PERMISSIONS_REQUEST_CODE = 124
    }
}
