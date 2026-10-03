package com.example.supercustomerid.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import com.example.supercustomerid.R
import com.example.supercustomerid.data.FirestoreRepository
import com.example.supercustomerid.data.effectiveStatus
import com.example.supercustomerid.databinding.ActivityHomeBinding
import com.example.supercustomerid.ui.packages.PackageActivity
import com.example.supercustomerid.ui.paymentmethod.PaymentMethodActivity
import com.example.supercustomerid.ui.support.SupportActivity
import com.example.supercustomerid.ui.login.LoginActivity
import com.example.supercustomerid.util.AccountStorage
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CUSTOMER_ID = "extra_customer_id"
    }

    private lateinit var binding: ActivityHomeBinding

    private val repository by lazy {
        FirestoreRepository()
    }

    private lateinit var customerId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHomeBinding.inflate(layoutInflater)

        setContentView(binding.root)

        com.example.supercustomerid.update.VersionChecker
            .checkForUpdate(this)

        customerId =
            intent.getStringExtra(EXTRA_CUSTOMER_ID) ?: ""

        if (customerId.isEmpty()) {

            loadPreviewData()

        } else {

            loadDashboard()
        }

        wireActions()
    }

    override fun onResume() {
        super.onResume()

        val selected =
            AccountStorage.getSelectedId(this)

        if (!selected.isNullOrEmpty() &&
            selected != customerId
        ) {

            customerId = selected

            loadDashboard()
        }

        if (selected.isNullOrEmpty() &&
            AccountStorage.hasAnyAccount(this).not()
        ) {

            if (customerId.isNotEmpty()) {

                startActivity(
                    Intent(
                        this,
                        LoginActivity::class.java
                    )
                )

                finish()
            }
        }
    }

    private fun loadPreviewData() {

        binding.tvCustomerId.text =
            "ABBAS001"

        binding.tvStatusChip.text =
            getString(R.string.status_active)

        binding.tvPackageSpeed.text =
            "6 Mbps"

        binding.tvIspProvider.text =
            "Powered by Ebone"

        binding.tvExpiryDate.text =
            "03 Sep"

        binding.tvValidTill.text =
            "Valid Till: 30 Aug 2026"
    }

    private fun loadDashboard() {

        lifecycleScope.launch {

            val customer =
                repository.getCustomer(customerId)
                    ?: return@launch

            binding.tvCustomerId.text =
                customer.customerId

            binding.tvPackageSpeed.text =
                customer.packageId

            binding.tvIspProvider.text =
                "Powered by ${
                    customer.ispProvider
                        .lowercase()
                        .replaceFirstChar {
                            it.uppercase()
                        }
                }"

            if (!customer.ispExpiryDate.isNullOrBlank()) {

                binding.layoutExpiryBadge.visibility =
                    View.VISIBLE

                binding.tvExpiryDate.text =
                    customer.ispExpiryDate

            } else {

                binding.layoutExpiryBadge.visibility =
                    View.GONE
            }

            when (customer.effectiveStatus()) {

                "ACTIVE" -> {

                    binding.tvStatusChip.text =
                        getString(R.string.status_active)

                    binding.tvStatusChip
                        .setBackgroundResource(
                            R.drawable.bg_chip_active
                        )

                    binding.tvStatusChip.setTextColor(
                        ContextCompat.getColor(
                            this@HomeActivity,
                            R.color.status_success_text
                        )
                    )
                }

                "PENDING_APPROVAL" -> {

                    binding.tvStatusChip.text =
                        getString(
                            R.string.status_pending_approval
                        )

                    binding.tvStatusChip
                        .setBackgroundResource(
                            R.drawable.bg_chip_pending
                        )
                }

                else -> {

                    binding.tvStatusChip.text =
                        getString(
                            R.string.status_disabled
                        )

                    binding.tvStatusChip
                        .setBackgroundResource(
                            R.drawable.bg_chip_disabled
                        )

                    binding.tvStatusChip.setTextColor(
                        ContextCompat.getColor(
                            this@HomeActivity,
                            R.color.status_error_text
                        )
                    )
                }
            }

            val paymentDue =
                customer.currentBalance <
                        customer.packagePrice

            binding.cardMakePayment.visibility =
                if (paymentDue) {

                    View.VISIBLE

                } else {

                    View.GONE
                }
        }
    }

    private fun wireActions() {

        binding.btnJoinNow.setOnClickListener {

            if (customerId.isEmpty()) {
                return@setOnClickListener
            }

            startActivity(
                Intent(
                    this,
                    PackageActivity::class.java
                ).putExtra(
                    PackageActivity.EXTRA_CUSTOMER_ID,
                    customerId
                )
            )
        }

        binding.btnPayNow.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    PaymentMethodActivity::class.java
                ).putExtra(
                    PaymentMethodActivity.EXTRA_CUSTOMER_ID,
                    customerId
                )
            )
        }

        binding.navHome.setOnClickListener {
            // Already on Home.
        }

        binding.navPayments.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    PaymentMethodActivity::class.java
                ).putExtra(
                    PaymentMethodActivity.EXTRA_CUSTOMER_ID,
                    customerId
                )
            )
        }

        binding.navHistory.setOnClickListener {
            // Future screen.
        }

        binding.navSupport.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    SupportActivity::class.java
                )
            )
        }

        binding.navProfile.setOnClickListener {
            // Future screen.
        }

        binding.tvCustomerId.setOnClickListener {
            showAccountSwitcherDialog()
        }

        binding.btnMenu.setOnClickListener {
            showLanguageDialog()
        }

        binding.btnInfo.setOnClickListener {
            // TODO: notifications
        }
    }

    private fun showAccountSwitcherDialog() {

        val accounts =
            AccountStorage.getRegisteredIds(this)

        if (accounts.isEmpty()) {

            startActivity(
                Intent(
                    this,
                    LoginActivity::class.java
                )
            )

            finish()

            return
        }

        val options =
            accounts.toMutableList()

        options.add(
            getString(
                R.string.action_add_connection
            )
        )

        options.add("Remove Account")

        AlertDialog.Builder(this)
            .setTitle(
                getString(
                    R.string.label_switch_connection
                )
            )
            .setItems(
                options.toTypedArray()
            ) { _, which ->

                if (which == accounts.size) {

                    startActivity(
                        Intent(
                            this,
                            LoginActivity::class.java
                        ).putExtra(
                            LoginActivity.EXTRA_ADD_MODE,
                            true
                        )
                    )

                    return@setItems
                }

                if (which == accounts.size + 1) {

                    showRemoveAccountDialog(accounts)

                    return@setItems
                }

                val chosen =
                    accounts[which]

                if (chosen != customerId) {

                    AccountStorage.setSelectedId(
                        this,
                        chosen
                    )

                    customerId = chosen

                    loadDashboard()
                }
            }
            .show()
    }

    private fun showRemoveAccountDialog(
        accounts: List<String>
    ) {

        AlertDialog.Builder(this)
            .setTitle("Remove Account")
            .setItems(
                accounts.toTypedArray()
            ) { _, which ->

                val accountToRemove =
                    accounts[which]

                showRemoveConfirmation(
                    accountToRemove
                )
            }
            .show()
    }

    private fun showRemoveConfirmation(
        accountToRemove: String
    ) {

        AlertDialog.Builder(this)
            .setTitle("Remove Account?")
            .setMessage(
                "Do you want to remove $accountToRemove from this device?"
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove") { _, _ ->

                removeAccountAndReplace(
                    accountToRemove
                )
            }
            .show()
    }

    private fun removeAccountAndReplace(
        accountToRemove: String
    ) {

        val wasCurrentAccount =
            accountToRemove == customerId

        val replacement =
            AccountStorage.removeAccount(
                this,
                accountToRemove
            )

        if (!wasCurrentAccount) {

            showAccountRemovedMessage(
                accountToRemove
            )

            return
        }

        if (!replacement.isNullOrEmpty()) {

            customerId =
                replacement

            loadDashboard()

            showAutomaticReplacementMessage(
                replacement
            )

            return
        }

        startActivity(
            Intent(
                this,
                LoginActivity::class.java
            )
        )

        finish()
    }

    private fun showAccountRemovedMessage(
        removedAccount: String
    ) {

        AlertDialog.Builder(this)
            .setTitle("Account Removed")
            .setMessage(
                "$removedAccount has been removed successfully."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showAutomaticReplacementMessage(
        replacementAccount: String
    ) {

        AlertDialog.Builder(this)
            .setTitle("Account Removed")
            .setMessage(
                "Account removed successfully.\n\n" +
                        "Dashboard automatically switched to:\n" +
                        replacementAccount
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showLanguageDialog() {

        val languages =
            arrayOf(
                "English",
                "اردو (Urdu)"
            )

        val codes =
            arrayOf(
                "en",
                "ur"
            )

        AlertDialog.Builder(this)
            .setTitle(
                getString(
                    R.string.label_select_language
                )
            )
            .setItems(
                languages
            ) { _, which ->

                AppCompatDelegate
                    .setApplicationLocales(
                        LocaleListCompat
                            .forLanguageTags(
                                codes[which]
                            )
                    )
            }
            .show()
    }
}
