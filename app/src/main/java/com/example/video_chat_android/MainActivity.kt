package com.example.video_chat_android

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    fun onJoinButtonClick(view: View) {
        val roomName = findViewById<EditText>(R.id.etRoomName).text.toString()
        if (roomName.isEmpty()) {
            Toast.makeText(this, "Please enter a room name", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, JoinRoomActivity::class.java)
        intent.putExtra("roomName", roomName)
        startActivity(intent)
    }
}