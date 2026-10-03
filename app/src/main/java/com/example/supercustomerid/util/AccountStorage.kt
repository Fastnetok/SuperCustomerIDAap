package com.example.supercustomerid.util

import android.content.Context
import org.json.JSONArray

object AccountStorage {

    private const val PREFS_NAME = "customer_accounts"
    private const val KEY_IDS = "registered_ids"
    private const val KEY_SELECTED = "selected_id"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getRegisteredIds(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_IDS, "[]") ?: "[]"
        val array = JSONArray(raw)
        return (0 until array.length()).map {
            array.getString(it)
        }
    }

    fun addAccount(
        context: Context,
        customerId: String
    ) {
        val current = getRegisteredIds(context).toMutableList()

        if (!current.contains(customerId)) {
            current.add(customerId)
        }

        val array = JSONArray(current)

        prefs(context)
            .edit()
            .putString(KEY_IDS, array.toString())
            .putString(KEY_SELECTED, customerId)
            .apply()
    }

    fun getSelectedId(context: Context): String? {
        val selected =
            prefs(context).getString(KEY_SELECTED, null)
        val ids = getRegisteredIds(context)
        val validSelected =
            selected?.takeIf { it in ids }

        if (validSelected != null) {
            return validSelected
        }

        val fallback = ids.firstOrNull()

        prefs(context)
            .edit()
            .putString(KEY_SELECTED, fallback)
            .apply()

        return fallback
    }

    fun setSelectedId(
        context: Context,
        customerId: String
    ) {
        val ids = getRegisteredIds(context)
        if (customerId !in ids) return

        prefs(context)
            .edit()
            .putString(KEY_SELECTED, customerId)
            .apply()
    }

    fun removeAccount(
        context: Context,
        customerId: String
    ): String? {
        val current =
            getRegisteredIds(context).toMutableList()

        if (!current.contains(customerId)) {
            return getSelectedId(context)
        }

        val selected =
            prefs(context).getString(KEY_SELECTED, null)

        val removedIndex =
            current.indexOf(customerId)

        current.remove(customerId)

        if (current.isEmpty()) {
            prefs(context)
                .edit()
                .remove(KEY_IDS)
                .remove(KEY_SELECTED)
                .apply()
            return null
        }

        if (selected != customerId) {
            val stillSelected =
                selected?.takeIf { it in current }
            val finalSelected =
                stillSelected ?: current.first()

            prefs(context)
                .edit()
                .putString(
                    KEY_IDS,
                    JSONArray(current).toString()
                )
                .putString(
                    KEY_SELECTED,
                    finalSelected
                )
                .apply()

            return finalSelected
        }

        val replacementIndex =
            if (removedIndex < current.size) {
                removedIndex
            } else {
                current.lastIndex
            }

        val replacement =
            current[replacementIndex]

        prefs(context)
            .edit()
            .putString(
                KEY_IDS,
                JSONArray(current).toString()
            )
            .putString(
                KEY_SELECTED,
                replacement
            )
            .apply()

        return replacement
    }

    fun hasAnyAccount(context: Context): Boolean {
        return getRegisteredIds(context).isNotEmpty()
    }
}
