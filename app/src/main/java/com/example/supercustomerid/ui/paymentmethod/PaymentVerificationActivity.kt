package com.example.supercustomerid.ui.paymentmethod

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.MediaStore
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.supercustomerid.BuildConfig
import com.example.supercustomerid.R
import com.example.supercustomerid.data.AiPaymentInterpreter
import com.example.supercustomerid.data.FirestoreRepository
import com.example.supercustomerid.data.OcrPaymentReader
import com.example.supercustomerid.data.PaymentSource
import com.example.supercustomerid.data.PaymentStatus
import com.example.supercustomerid.data.PaymentRules
import com.example.supercustomerid.data.PaymentTransaction
import com.example.supercustomerid.data.SmsPaymentParser
import com.example.supercustomerid.databinding.ActivityPaymentVerificationBinding
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

class PaymentVerificationActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CUSTOMER_ID = "extra_customer_id"
        const val EXTRA_METHOD_NAME = "extra_method_name"
        const val EXTRA_AMOUNT = "extra_amount"
        private const val PROCESSING_MILLIS = 30 * 60 * 1000L
    }

    private lateinit var binding: ActivityPaymentVerificationBinding
    private val repository by lazy { FirestoreRepository() }
    private var customerId: String = ""
    private var methodName: String = ""
    private var amount: Double = 0.0
    private var countDownTimer: CountDownTimer? = null
    private var transactionListener: ListenerRegistration? = null
    private var screenshotVerified: Boolean = false

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) handleScreenshotSelected(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        customerId = intent.getStringExtra(EXTRA_CUSTOMER_ID) ?: ""
        methodName = intent.getStringExtra(EXTRA_METHOD_NAME) ?: "Payment"
        amount = intent.getDoubleExtra(EXTRA_AMOUNT, 0.0)

        binding.tvMethodTitle.text = "$methodName Payment"

        binding.tvDetectedDateTime.text = "—"
        binding.tvDetectedAmount.text = "Rs. %.0f".format(if (amount > 0) amount else 1500.0)
        binding.tvDetectedName.text = "—"
        binding.tvDetectedPhone.text = "—"

        binding.btnBack.setOnClickListener { finish() }

        binding.btnUploadScreenshot.setOnClickListener {
            val tid = binding.etTransactionId.text.toString().trim()
            val senderName = binding.etSenderName.text.toString().trim()
            if (tid.isEmpty() && senderName.isEmpty()) {
                showScreenshotStatus(getString(R.string.msg_enter_tid_first), isError = true)
                return@setOnClickListener
            }
            pickImageLauncher.launch("image/*")
        }

        binding.btnVerifyAndActivate.setOnClickListener { onVerifyClicked() }
    }

    private fun handleScreenshotSelected(uri: Uri) {
        val enteredTid = binding.etTransactionId.text.toString().trim()
        showScreenshotStatus(getString(R.string.msg_ocr_reading), isError = false)

        val bitmap: Bitmap = try {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(contentResolver, uri)
        } catch (e: Exception) {
            showScreenshotStatus(getString(R.string.msg_ai_failed), isError = true)
            return
        }

        OcrPaymentReader.readFromBitmap(
            bitmap,
            onSuccess = { parsed -> compareAndShowResult(parsed.rawText, enteredTid) },
            onAmbiguous = { rawText -> fallbackToAi(rawText, enteredTid) },
            onFailure = { fallbackToAi("", enteredTid) }
        )
    }

    private fun fallbackToAi(rawText: String, enteredTid: String) {
        val apiKey = BuildConfig.OPENAI_API_KEY
        if (apiKey.isBlank()) {
            showScreenshotStatus(getString(R.string.msg_ai_failed), isError = true)
            return
        }
        showScreenshotStatus(getString(R.string.msg_ai_reading), isError = false)

        AiPaymentInterpreter.interpret(
            rawText = rawText,
            apiKey = apiKey,
            onResult = { parsed: SmsPaymentParser.ParsedResult ->
                runOnUiThread { compareAndShowResult(parsed.rawText.ifBlank { rawText }, enteredTid) }
            },
            onError = {
                runOnUiThread { showScreenshotStatus(getString(R.string.msg_ai_failed), isError = true) }
            }
        )
    }

    private fun compareAndShowResult(rawText: String, enteredTid: String) {
        if (enteredTid.isNotEmpty()) {
            val candidates = SmsPaymentParser.parseAllTidCandidates(rawText)
            val tidMatches = candidates.any { it.equals(enteredTid.trim(), ignoreCase = true) }

            if (!tidMatches) {
                screenshotVerified = false
                val foundText = if (candidates.isNotEmpty()) candidates.joinToString(", ") else "(none found)"
                showScreenshotStatus(
                    "TID mismatch.\nYou entered: $enteredTid\nFound in screenshot: $foundText",
                    isError = true
                )
                return
            }
        }

        val detectedAmount = if (enteredTid.isNotEmpty())
            SmsPaymentParser.amountNearTid(rawText, enteredTid)
        else
            SmsPaymentParser.parse(rawText).amount
        if (detectedAmount != null && amount > 0) {
            val paymentStatus = PaymentRules.evaluatePaymentStatus(detectedAmount, amount)
            when (paymentStatus) {
                PaymentStatus.INSUFFICIENT -> {
                    screenshotVerified = false
                    showScreenshotStatus(
                        getString(R.string.msg_underpayment, "%.0f".format(amount)),
                        isError = true
                    )
                    return
                }
                PaymentStatus.OVERPAID -> {
                    screenshotVerified = false
                    showScreenshotStatus(
                        getString(R.string.msg_overpaid, "%.0f".format(amount)),
                        isError = true
                    )
                    return
                }
                else -> {}
            }
        }

        screenshotVerified = true
        showScreenshotStatus(getString(R.string.msg_tid_match), isError = false)
    }

    private fun showScreenshotStatus(message: String, isError: Boolean) {
        hideKeyboard()
        binding.tvScreenshotStatus.visibility = View.VISIBLE
        binding.tvScreenshotStatus.text = message
        if (isError) {
            binding.tvScreenshotStatus.setBackgroundResource(R.drawable.bg_status_error)
            binding.tvScreenshotStatus.setTextColor(ContextCompat.getColor(this, R.color.status_error_text))
        } else {
            binding.tvScreenshotStatus.setBackgroundResource(R.drawable.bg_status_success)
            binding.tvScreenshotStatus.setTextColor(ContextCompat.getColor(this, R.color.status_success_text))
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
        currentFocus?.clearFocus()
    }

    private fun onVerifyClicked() {
        val tid = binding.etTransactionId.text.toString().trim()
        val senderName = binding.etSenderName.text.toString().trim()

        if (tid.isEmpty() && senderName.isEmpty()) {
            showScreenshotStatus(
                "Please enter your Transaction ID, or at least your Sender Name.",
                isError = true
            )
            return
        }

        binding.btnVerifyAndActivate.isEnabled = false

        if (customerId.isNotEmpty()) {
            lifecycleScope.launch {
                if (tid.isNotEmpty() && repository.isTidAlreadyUsed(tid)) {
                    showScreenshotStatus(
                        "This Transaction ID has already been used. Duplicate or fake entries are not allowed.",
                        isError = true
                    )
                    binding.btnVerifyAndActivate.isEnabled = true
                    return@launch
                }

                try {
                    val transactionId = repository.recordPayment(
                        PaymentTransaction(
                            customerId = customerId,
                            source = mapMethodToSource(methodName),
                            amount = amount,
                            bankTransactionId = tid,
                            senderName = binding.etSenderName.text.toString().trim().ifEmpty { null },
                            status = PaymentStatus.PENDING
                        )
                    )
                    startListeningForVerification(transactionId)
                    showProcessingState()
                } catch (e: Exception) {
                    binding.btnVerifyAndActivate.isEnabled = true
                    showScreenshotStatus("Could not save your payment: ${e.message}", isError = true)
                    return@launch
                }
            }
        } else {
            showProcessingState()
        }
    }

    private fun startListeningForVerification(transactionId: String) {
        transactionListener?.remove()
        transactionListener = repository.listenToTransactionStatus(transactionId) { status ->
            when (status) {
                PaymentStatus.VERIFIED -> {
                    countDownTimer?.cancel()
                    showSuccessState()
                }
                PaymentStatus.FAILED -> {
                    countDownTimer?.cancel()
                    showScreenshotStatus(
                        "We couldn't activate your package automatically. Our team has been notified — please contact support if this continues.",
                        isError = true
                    )
                }
                PaymentStatus.INSUFFICIENT, PaymentStatus.OVERPAID -> {
                    countDownTimer?.cancel()
                    showScreenshotStatus(getString(R.string.msg_tid_mismatch), isError = true)
                }
                PaymentStatus.PENDING -> {}
            }
        }
    }

    private fun mapMethodToSource(name: String): PaymentSource = when (name) {
        "Easypaisa" -> PaymentSource.EASYPAISA
        "JazzCash" -> PaymentSource.JAZZCASH
        "SadaPay" -> PaymentSource.SADAPAY
        "Faysal Bank" -> PaymentSource.FAYSAL_BANK
        "Raast ID" -> PaymentSource.RAAST_ID
        "Bank Alfalah" -> PaymentSource.BANK_ALFALAH
        else -> PaymentSource.MANUAL_BANK
    }

    private fun showProcessingState() {
        binding.cardStatus.visibility = View.VISIBLE
        binding.tvStatusTitle.text = getString(R.string.status_processing_title)
        binding.tvStatusSubtitle.text = getString(R.string.status_processing_subtitle)

        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(PROCESSING_MILLIS, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = millisUntilFinished / 60000
                val seconds = (millisUntilFinished / 1000) % 60
                binding.tvStatusTimer.text = "%02d:%02d".format(minutes, seconds)
            }

            override fun onFinish() {
                binding.tvStatusTimer.text = "00:00"
                binding.tvStatusSubtitle.text = getString(R.string.status_still_processing)
            }
        }.start()
    }

    private fun showSuccessState() {
        binding.tvStatusTimer.text = "00:00"
        binding.tvStatusTitle.text = getString(R.string.status_success_title)
        binding.tvStatusSubtitle.text = getString(R.string.status_success_subtitle)
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        transactionListener?.remove()
    }
}
