package com.luisell.bestkeyboard

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

/**
 * Ventanita "Mis contraseñas": eliges una, confirmas con tu huella o PIN, y el teclado la escribe.
 */
class VaultPickActivity : Activity() {

    private var chosen: String? = null
    private val en get() = Locale.getDefault().language != "es"
    private fun tr(es: String, en: String) = if (this.en) en else es
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(android.R.style.Theme_Material_Dialog_NoActionBar)
        super.onCreate(savedInstanceState)
        VaultStore.load(this)
        val list = VaultStore.passwords()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(20), dp(20), dp(20), dp(12))
        root.background = GradientDrawable().apply { setColor(MainActivity.CARD); cornerRadius = dp(24).toFloat() }
        val title = TextView(this)
        title.text = tr("Mis contraseñas", "My passwords")
        title.textSize = 20f
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
        title.setTextColor(MainActivity.TEXT)
        root.addView(title)
        val sub = TextView(this)
        sub.text = tr("Elige una. Se pide tu huella o PIN antes de escribirla.", "Pick one. Your fingerprint or PIN is asked before typing it.")
        sub.textSize = 13f
        sub.setTextColor(MainActivity.SUB)
        sub.setPadding(0, dp(4), 0, dp(10))
        root.addView(sub)

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        for (e in list) {
            val b = TextView(this)
            b.text = "🔒  " + e.label.ifBlank { tr("Contraseña", "Password") }
            b.textSize = 16f
            b.setTextColor(MainActivity.TEXT)
            b.gravity = Gravity.CENTER_VERTICAL
            b.setPadding(dp(16), dp(14), dp(16), dp(14))
            b.background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x334FD1FF),
                GradientDrawable().apply { setColor(MainActivity.CARD2); cornerRadius = dp(18).toFloat() }, null,
            )
            b.setOnClickListener { pick(e.value) }
            col.addView(b, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
        val sv = ScrollView(this)
        sv.addView(col)
        root.addView(sv, LinearLayout.LayoutParams(-1, -2))

        val cancel = TextView(this)
        cancel.text = tr("Cancelar", "Cancel")
        cancel.textSize = 15f
        cancel.setTextColor(MainActivity.CYAN)
        cancel.gravity = Gravity.CENTER
        cancel.setPadding(0, dp(14), 0, dp(6))
        cancel.setOnClickListener { finish() }
        root.addView(cancel, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
        window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    /** Pide la huella / PIN del teléfono; si sale bien, el teclado la escribe al volver. */
    private fun pick(value: String) {
        chosen = value
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!km.isDeviceSecure) { done(); return } // el teléfono no tiene bloqueo: no hay nada que pedir
        val cb = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) { done() }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) { chosen = null }
        }
        try {
            when {
                Build.VERSION.SDK_INT >= 30 -> BiometricPrompt.Builder(this)
                    .setTitle(tr("Confirma que eres tú", "Confirm it's you"))
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build().authenticate(CancellationSignal(), mainExecutor, cb)
                Build.VERSION.SDK_INT == 29 -> @Suppress("DEPRECATION") BiometricPrompt.Builder(this)
                    .setTitle(tr("Confirma que eres tú", "Confirm it's you"))
                    .setDeviceCredentialAllowed(true)
                    .build().authenticate(CancellationSignal(), mainExecutor, cb)
                else -> @Suppress("DEPRECATION") startActivityForResult(
                    km.createConfirmDeviceCredentialIntent(tr("Confirma que eres tú", "Confirm it's you"), null), 1,
                )
            }
        } catch (_: Exception) {
            @Suppress("DEPRECATION")
            startActivityForResult(km.createConfirmDeviceCredentialIntent(tr("Confirma que eres tú", "Confirm it's you"), null), 1)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK) done() else chosen = null
    }

    private fun done() {
        VaultStore.pendingInsert = chosen
        finish()
    }
}
