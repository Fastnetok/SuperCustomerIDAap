package com.example.supercustomerid.ui.support

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.supercustomerid.databinding.ActivitySupportBinding

class SupportActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySupportBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySupportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        binding.cardWhatsapp.setOnClickListener {
            val phone = "923007951912"
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone")))
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:0300-7951912")))
            }
        }

        binding.cardComplaint.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:0321-1119966")))
        }
    }
}
