package com.example.supercustomerid.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

/**
 * Single place for all Firestore reads/writes with absolute company/zone isolation.
 * Collections are scoped under:
 *   companies/{companyId}/customers/{customerId}
 *   companies/{companyId}/transactions/{transactionId}
 */
class FirestoreRepository {

    private val db = FirebaseFirestore.getInstance()

    /**
     * Resolves customer and company ID using collectionGroup query across all companies
     * (`companies/{companyId}/customers`) to ensure absolute multi-company data isolation.
     */
    suspend fun getCustomer(customerId: String): Customer? {
        if (customerId.isBlank()) return null
        val snapshot = db.collectionGroup("customers")
            .whereEqualTo("customerId", customerId)
            .get()
            .await()
        return snapshot.documents.firstOrNull()?.toObject(Customer::class.java)
    }

    private suspend fun getCustomerCompanyRef(customerId: String): DocumentReference? {
        val snapshot = db.collectionGroup("customers")
            .whereEqualTo("customerId", customerId)
            .get()
            .await()
        return snapshot.documents.firstOrNull()?.reference
    }

    /**
     * GPS is captured ONCE — only on first activation.
     * If locationCapturedAt already exists, this call is a no-op.
     */
    suspend fun saveLocationIfFirstActivation(customerId: String, lat: Double, lng: Double) {
        val docRef = getCustomerCompanyRef(customerId) ?: return
        val snap = docRef.get().await()
        val customer = snap.toObject(Customer::class.java)
        if (customer?.locationCapturedAt != null) {
            return // already captured once, never overwrite
        }
        docRef.update(
            mapOf(
                "latitude" to lat,
                "longitude" to lng,
                "locationCapturedAt" to System.currentTimeMillis()
            )
        ).await()
    }

    suspend fun recordPayment(transaction: PaymentTransaction): String {
        // Resolve company from customerId to store transaction in the correct company's zone
        val customerDoc = getCustomerCompanyRef(transaction.customerId)
        val companyRef = customerDoc?.parent?.parent ?: db.collection("companies").document("ebone")
        val transactionsRef = companyRef.collection("transactions")

        val docRef = transactionsRef.document()
        val withId = transaction.copy(transactionId = docRef.id)
        docRef.set(withId).await()
        return docRef.id
    }

    /**
     * Real-time listener on a single transaction's status across companies.
     */
    fun listenToTransactionStatus(
        transactionId: String,
        onStatusChanged: (PaymentStatus) -> Unit
    ): ListenerRegistration {
        return db.collectionGroup("transactions")
            .whereEqualTo("transactionId", transactionId)
            .addSnapshotListener { snapshot, _ ->
                val doc = snapshot?.documents?.firstOrNull() ?: return@addSnapshotListener
                val statusName = doc.getString("status") ?: return@addSnapshotListener
                val status = runCatching { PaymentStatus.valueOf(statusName) }.getOrNull()
                if (status != null) onStatusChanged(status)
            }
    }

    private val dealerTransactionsRef = db.collectionGroup("dealerTransactions")
    private val suspiciousActivityRef = db.collection("suspiciousActivity")

    suspend fun isTidAlreadyUsed(tid: String): Boolean {
        val snapshot = db.collectionGroup("transactions")
            .whereEqualTo("bankTransactionId", tid)
            .whereIn("status", listOf("VERIFIED", "PENDING"))
            .get()
            .await()
        if (!snapshot.isEmpty) return true

        val dealerSnapshot = dealerTransactionsRef
            .whereEqualTo("bankTransactionId", tid)
            .whereIn("status", listOf("PENDING", "VERIFIED", "NEEDS_REVIEW"))
            .get()
            .await()
        if (!dealerSnapshot.isEmpty) {
            val dealerId = dealerSnapshot.documents.firstOrNull()?.getString("dealerId") ?: "unknown"
            suspiciousActivityRef.add(
                mapOf(
                    "type" to "CROSS_APP_TID_REUSE_ATTEMPT",
                    "detail" to "TID already used for a dealer franchise-balance claim; attempted again in SuperCustomerIDAap.",
                    "bankTransactionId" to tid,
                    "dealerId" to dealerId,
                    "detectedAt" to System.currentTimeMillis()
                )
            )
            return true
        }
        return false
    }

    sealed class ClaimResult {
        object Success : ClaimResult()
        object AlreadyRegisteredOnThisDevice : ClaimResult()
        object NotFound : ClaimResult()
        object WrongPin : ClaimResult()
        object LinkedToAnotherDevice : ClaimResult()
    }

    suspend fun claimCustomerId(customerId: String, pin: String, deviceId: String): ClaimResult {
        val docRef = getCustomerCompanyRef(customerId) ?: return ClaimResult.NotFound
        val customer = docRef.get().await().toObject(Customer::class.java) ?: return ClaimResult.NotFound

        if (customer.linkedDeviceId == deviceId) return ClaimResult.AlreadyRegisteredOnThisDevice
        if (!customer.linkedDeviceId.isNullOrEmpty()) return ClaimResult.LinkedToAnotherDevice
        if (customer.registrationPin != pin) return ClaimResult.WrongPin

        docRef.update(
            mapOf(
                "linkedDeviceId" to deviceId,
                "registrationPin" to null
            )
        ).await()
        return ClaimResult.Success
    }

    fun evaluatePaymentStatus(amountPaid: Double, packagePrice: Double): PaymentStatus =
        PaymentRules.evaluatePaymentStatus(amountPaid, packagePrice)

    suspend fun updateCustomerBalanceAndRecharge(customerId: String, amountPaid: Double, packagePrice: Double) {
        val status = evaluatePaymentStatus(amountPaid, packagePrice)
        if (status != PaymentStatus.VERIFIED) return
        val docRef = getCustomerCompanyRef(customerId) ?: return
        docRef.update(
            mapOf(
                "currentBalance" to amountPaid,
                "isActive" to true,
                "activationStatus" to "ACTIVE",
                "lastPaymentDate" to System.currentTimeMillis()
            )
        ).await()
    }
}
