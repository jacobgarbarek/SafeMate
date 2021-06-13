package com.example.dms_assignment4mobileclient

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText

class LoginActivity : AppCompatActivity() {
    private lateinit var editTextUserName : EditText
    private lateinit var loginButton : Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        editTextUserName = findViewById(R.id.username_input)
        loginButton = findViewById(R.id.login_button)

        loginButton.setOnClickListener {
            val userName : String = editTextUserName.text.toString()
            val intent = Intent(this, MapsActivity::class.java)
            intent.putExtra("username", userName)
            startActivity(intent)
            finish()        //cannot go back to login screen
        }
    }
}