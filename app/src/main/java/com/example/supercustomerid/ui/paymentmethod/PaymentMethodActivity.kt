package com.example.supercustomerid.ui.paymentmethod

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.supercustomerid.data.FirestoreRepository
import com.example.supercustomerid.databinding.ActivityPaymentMethodBinding
import kotlinx.coroutines.launch

class PaymentMethodActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CUSTOMER_ID = "extra_customer_id"
    }

    private lateinit var binding: ActivityPaymentMethodBinding
    private val repository by lazy { FirestoreRepository() }
    private var customerId: String = ""
    private var outstandingAmount: Double = 1500.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentMethodBinding.inflate(layoutInflater)
        setContentView(binding.root)

        customerId = intent.getStringExtra(EXTRA_CUSTOMER_ID) ?: ""

        if (customerId.isNotEmpty()) {
            binding.tvOutstandingAmount.text = "Rs. …"
            loadOutstandingAmount()
        } else {
            binding.tvOutstandingAmount.text = "Rs. %.2f".format(outstandingAmount)
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.rowEasypaisa.setOnClickListener { openVerification("Easypaisa") }
        binding.rowJazzCash.setOnClickListener { openVerification("JazzCash") }
        binding.rowSadaPay.setOnClickListener { openVerification("SadaPay") }
        binding.rowFaysalBank.setOnClickListener { openVerification("Faysal Bank") }
        binding.rowRaastId.setOnClickListener { openVerification("Raast ID") }
        binding.rowBankAlfalah.setOnClickListener { openVerification("Bank Alfalah") }
        binding.rowOtherBank.setOnClickListener { openVerification("Other Bank Transfer") }

        binding.btnAddMoreBanks.setOnClickListener {
            // Admin-configurable additional manual banks
        }
    }

    private fun loadOutstandingAmount() {
        lifecycleScope.launch {
            val customer = repository.getCustomer(customerId) ?: return@launch
            outstandingAmount = (customer.packagePrice - customer.currentBalance).coerceAtLeast(0.0)
            binding.tvOutstandingAmount.text = "Rs. %.2f".format(outstandingAmount)
        }
    }

    private fun openVerification(methodName: String) {
        startActivity(
            Intent(this, PaymentVerificationActivity::class.java)
                .putExtra(PaymentVerificationActivity.EXTRA_CUSTOMER_ID, customerId)
                .putExtra(PaymentVerificationActivity.EXTRA_METHOD_NAME, methodName)
                .putExtra(PaymentVerificationActivity.EXTRA_AMOUNT, outstandingAmount)
        )
    }
}
