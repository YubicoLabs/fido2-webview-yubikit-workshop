package com.yubico.yubikit_fido_demo.yubikit

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.EditText
import com.yubico.yubikit_fido_demo.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

suspend fun showError(context: Context, errorMessage: String) =
    withContext(Dispatchers.Main) {
        suspendCoroutine { cont ->
            AlertDialog.Builder(context)
                .setTitle("Error")
                .setMessage(errorMessage)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    cont.resume(Unit)
                }
                .setOnCancelListener {
                    cont.resume(Unit)
                }
                .create()
                .show()
        }
    }

/**
 * Asks the user for a PIN/password.
 */
suspend fun enterPin(context: Context, title: String): CharArray =
    withContext(Dispatchers.Main) {
        suspendCoroutine { cont ->
            val view = LayoutInflater.from(context).inflate(R.layout.dialog_pin, null)
            AlertDialog.Builder(context)
                .setTitle(title)
                .setView(view)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val pin =
                        view.findViewById<EditText>(R.id.dialog_pin_edittext).text.toString()
                    cont.resume(pin.toCharArray())
                }
                .setNeutralButton(android.R.string.cancel) { dialog, _ ->
                    dialog.cancel()
                }
                .setOnCancelListener {
                    cont.resumeWithException(CancellationException())
                }
                .create()
                .show()
        }
    }

/**
 * Asks the user to choose an item from a list.
 */
suspend fun <T> selectItem(
    context: Context,
    title: String,
    items: List<T>,
    label: (T) -> String
): Int =
    withContext(Dispatchers.Main) {
        suspendCoroutine { cont ->
            AlertDialog.Builder(context)
                .setTitle(title)
                .setItems(items.map { label(it) }.toTypedArray()) { _, value ->
                    cont.resume(value)
                }
                .setOnCancelListener {
                    cont.resumeWithException(CancellationException())
                }
                .create().apply {
                    listView.apply {
                        dividerHeight = 2
                    }
                }.show()
        }
    }