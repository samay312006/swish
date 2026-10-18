package com.example.swish

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * A see-through screen that only shows the "Heart contact" dialog.
 * Opened when a heart is drawn before a number is saved, by long-pressing the
 * floating bubble, or from the app icon's long-press shortcut.
 */
class HeartContactActivity : AppCompatActivity() {

    private lateinit var input: EditText
    private var dialog: AlertDialog? = null

    // ACTION_PICK on the phone-number table grants temporary read access to the
    // chosen entry, so no READ_CONTACTS permission is needed.
    private val pickContact = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@registerForActivityResult
        contentResolver.query(
            uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                input.setText(cursor.getString(0))
                input.setSelection(input.text.length)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dialAfterSave = intent.getBooleanExtra(EXTRA_DIAL_AFTER_SAVE, false)
        val padding = (24 * resources.displayMetrics.density).toInt()

        input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_PHONE
            hint = "Phone number"
            setText(savedInstanceState?.getString(STATE_INPUT) ?: HeartContact.get(this@HeartContactActivity).orEmpty())
            setSelection(text.length)
        }
        val container = FrameLayout(this).apply {
            setPadding(padding, padding / 3, padding, 0)
            addView(input)
        }

        val message = if (dialAfterSave) {
            "You drew a heart! Choose who it should call. The dialer opens with this number, and you still press call yourself."
        } else {
            "The number the dialer opens with when you draw a heart. You still press call yourself."
        }

        dialog = MaterialAlertDialogBuilder(this)
            .setTitle("❤️ Heart contact")
            .setMessage(message)
            .setView(container)
            .setPositiveButton("Save", null)
            .setNeutralButton("Pick contact", null)
            .setNegativeButton("Cancel", null)
            .setOnDismissListener { finish() }
            .show()

        // Replace the default click handlers so these buttons don't close the dialog.
        dialog?.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
            pickContact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
        }
        dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            val number = input.text.toString().trim()
            if (number.none { it.isDigit() }) {
                input.error = "Enter a phone number"
                return@setOnClickListener
            }
            HeartContact.set(this, number)
            Toast.makeText(this, "Heart contact saved", Toast.LENGTH_SHORT).show()
            if (dialAfterSave) {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}")))
            }
            dialog?.dismiss()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_INPUT, input.text.toString())
    }

    override fun onDestroy() {
        // Avoid leaking the window when the activity is recreated (e.g. on rotation).
        dialog?.setOnDismissListener(null)
        dialog?.dismiss()
        super.onDestroy()
    }

    companion object {
        /** When true, open the dialer with the new number right after saving. */
        const val EXTRA_DIAL_AFTER_SAVE = "dial_after_save"
        private const val STATE_INPUT = "input"
    }
}
