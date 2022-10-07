package com.example.video_chat_android

import android.app.Application
import android.opengl.GLSurfaceView
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.example.video_chat_android.network.APIService
import com.example.video_chat_android.network.GetSessionResponse
import com.opentok.android.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class JoinRoomActivityViewModel(application: Application) : AndroidViewModel(application){
    private val TAG = "JoinRoomViewModel"
    private var retrofit: Retrofit? = null
    private var apiService: APIService? = null

    var session: Session? = null
    var publisher: Publisher? = null
    var subscriber: Subscriber? = null

    var isPubActive: MutableLiveData<Boolean> = MutableLiveData()
    var isSubActive: MutableLiveData<Boolean> = MutableLiveData()

    var fullScreenView = "subscriber"

    private val publisherListener: PublisherKit.PublisherListener = object :
        PublisherKit.PublisherListener {
        override fun onStreamCreated(publisherKit: PublisherKit, stream: Stream) {
            Log.i(TAG, "onStreamCreated: Publisher Stream Created. Own stream ${stream.streamId}")
            isPubActive.value = true
        }

        override fun onStreamDestroyed(publisherKit: PublisherKit, stream: Stream) {
            Log.i(TAG, "onStreamDestroyed: Publisher Stream Destroyed. Own stream ${stream.streamId}")
        }

        override fun onError(publisherKit: PublisherKit, opentokError: OpentokError) {
            finishWithMessage("PublisherKit onError: ${opentokError.message}")
        }
    }

    private val sessionListener: Session.SessionListener = object : Session.SessionListener {
        override fun onConnected(session: Session) {
            Log.i(TAG, "onConnected: Connected to session: ${session.sessionId}")
            publisher = Publisher.Builder(application).build()

            publisher?.setPublisherListener(publisherListener)
            publisher?.renderer?.setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE, BaseVideoRenderer.STYLE_VIDEO_FILL)

            if (publisher?.view is GLSurfaceView) {
                (publisher?.view as GLSurfaceView).setZOrderOnTop(true)
            }

            session.publish(publisher)
        }

        override fun onDisconnected(session: Session) {
            Log.i(TAG, "onDisconnected: Disconnected from session: ${session.sessionId}")
        }

        override fun onStreamReceived(session: Session, stream: Stream) {
            Log.i(TAG, "onStreamReceived: New Stream Received ${stream.streamId} in session: ${session.sessionId}")
            if (subscriber == null) {
                subscriber = Subscriber.Builder(application, stream).build().also {
                    it.renderer?.setStyle(
                        BaseVideoRenderer.STYLE_VIDEO_SCALE,
                        BaseVideoRenderer.STYLE_VIDEO_FILL
                    )

                    it.setSubscriberListener(subscriberListener)
                }

                session.subscribe(subscriber)
                isSubActive.value = true
            }
        }

        override fun onStreamDropped(session: Session, stream: Stream) {
            Log.i(TAG, "onStreamDropped: Stream Dropped: ${stream.streamId} in session: ${session.sessionId}")
            if (subscriber != null) {
                subscriber = null
            }
        }

        override fun onError(session: Session, opentokError: OpentokError) {
            finishWithMessage("Session error: ${opentokError.message}")
        }
    }

    var subscriberListener: SubscriberKit.SubscriberListener = object :
        SubscriberKit.SubscriberListener {
        override fun onConnected(subscriberKit: SubscriberKit) {
            Log.i(TAG, "onConnected: Subscriber connected. Stream: ${subscriberKit.stream.streamId}")
        }

        override fun onDisconnected(subscriberKit: SubscriberKit) {
            Log.i(TAG, "onDisconnected: Subscriber disconnected. Stream: ${subscriberKit.stream.streamId}")
        }

        override fun onError(subscriberKit: SubscriberKit, opentokError: OpentokError) {
            finishWithMessage("SubscriberKit onError: ${opentokError.message}")
        }
    }

     fun initRetrofit() {
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

    fun getSession(roomName: String) {
        Log.i(TAG, "getSession")

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
        session = Session.Builder(getApplication(), apiKey, sessionId)
            .sessionOptions(object : Session.SessionOptions() {
                override fun useTextureViews(): Boolean {
                    return true
                }
            }).build().also {
                it.setSessionListener(sessionListener)
                it.connect(token)
            }
    }

    private fun finishWithMessage(message: String) {
        Log.e(TAG, message)
        Toast.makeText(getApplication(), message, Toast.LENGTH_LONG).show()
    }
}