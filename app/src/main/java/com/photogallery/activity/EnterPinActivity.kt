package com.photogallery.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.photogallery.MyApplication.Companion.setupTooltip
import com.photogallery.R
import com.photogallery.base.BaseActivity
import com.photogallery.databinding.ActivityEnterPinBinding
import com.skydoves.balloon.ArrowOrientation
import java.security.MessageDigest
import java.util.concurrent.Executor

class EnterPinActivity : BaseActivity<ActivityEnterPinBinding>() {

    private lateinit var tvForgotPin: TextView
    private lateinit var ivDigitOne: ImageView
    private lateinit var ivDigitTwo: ImageView
    private lateinit var ivDigitThree: ImageView
    private lateinit var ivDigitFour: ImageView
    private var pin = ""
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    override fun getViewBinding(): ActivityEnterPinBinding {
        return ActivityEnterPinBinding.inflate(layoutInflater)
    }

    override fun init(savedInstanceState: Bundle?) {
        tvForgotPin = binding.tvForgotPin
        ivDigitOne = binding.ivDigitOne
        ivDigitTwo = binding.ivDigitTwo
        ivDigitThree = binding.ivDigitThree
        ivDigitFour = binding.ivDigitFour

        binding.toolbarEnterPin.tvToolbarTitle.text = getString(R.string.enter_pin)
        setupKeypad()
        binding.btnSubmit.setOnClickListener {
            if (pin.length == 4) {
                verifyPin(pin)
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.please_enter_a_4_digit_pin), Toast.LENGTH_SHORT
                ).show()
            }
        }

        tvForgotPin.setOnClickListener {
            val recoveryEmail = ePreferences.getString("recovery_email", "")
            if (recoveryEmail!!.isEmpty()) {
                Toast.makeText(this, getString(R.string.no_recovery_email_set), Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            val intent = Intent(this, ForgotPinActivity::class.java)
            intent.putExtra("recoveryEmail", recoveryEmail)
            startActivity(intent)
            nextScreenAnimation()
        }

        if (ePreferences.getBoolean("fingerprint_enabled", false)) {
            binding.tvUseFingerPrint.visibility = View.VISIBLE
        }

        setupBiometricAuthentication()
    }

    override fun addListener() {
        binding.toolbarEnterPin.ivBack.setOnClickListener {
            onBackPressedDispatcher()
        }

        binding.tvUseFingerPrint.setOnClickListener {
            if (ePreferences.getBoolean("fingerprint_enabled", false)) {
                biometricPrompt.authenticate(promptInfo)
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.fingerprint_authentication_is_disabled), Toast.LENGTH_SHORT
                )
                    .show()
            }
        }
    }

    override fun onBackPressedDispatcher() {
        backScreenAnimation()
        finish()
    }

    private fun setupKeypad() {
        val buttons = listOf(
            binding.btn0, binding.btn1, binding.btn2, binding.btn3, binding.btn4,
            binding.btn5, binding.btn6, binding.btn7, binding.btn8, binding.btn9,
            binding.btnBackspace
        )

        buttons.forEach { button ->
            button.setOnClickListener {
                when (button.id) {
                    R.id.btnBackspace -> {
                        if (pin.isNotEmpty()) {
                            pin = pin.dropLast(1)
                            updatePinIndicators()
                        }
                    }

                    else -> {
                        if (pin.length < 4) {
                            pin += button.text
                            updatePinIndicators()
                        }
                    }
                }
            }
        }
    }

    private fun updatePinIndicators() {
        val defaultColor = ContextCompat.getColor(this, android.R.color.transparent)
        val activeColor = getAttributeColor(R.attr.colorApp)

        ivDigitOne.setColorFilter(if (pin.length >= 1) activeColor else defaultColor)
        ivDigitTwo.setColorFilter(if (pin.length >= 2) activeColor else defaultColor)
        ivDigitThree.setColorFilter(if (pin.length >= 3) activeColor else defaultColor)
        ivDigitFour.setColorFilter(if (pin.length >= 4) activeColor else defaultColor)
    }

    private fun verifyPin(pin: String) {
        if (!ePreferences.getBoolean("PinSeated", false)) {
            Toast.makeText(
                this,
                getString(R.string.no_pin_set_please_set_a_pin_first), Toast.LENGTH_SHORT
            ).show()
            startActivity(Intent(this, SetPinActivity::class.java))
            nextScreenAnimation()
            finish()
            return
        }

        val storedPinHash = ePreferences.getString("hashed_pin", "")
        val inputPinHash = hashPin(pin)
        if (storedPinHash == inputPinHash) {
            startActivity(Intent(this, LockedImagesActivity::class.java))
            nextScreenAnimation()
            finish()
        } else {
            Toast.makeText(this, getString(R.string.incorrect_pin), Toast.LENGTH_SHORT).show()
            this@EnterPinActivity.pin = ""
            updatePinIndicators()
            tvForgotPin.visibility = TextView.VISIBLE
            if (ePreferences.getBoolean("isFirstTimeForgotPin", true)) {
                setupTooltip(
                    this,
                    binding.tvForgotPin,
                    getString(R.string.click_to_reset_your_pin),
                    ArrowOrientation.TOP,
                    ePreferences,
                    "isFirstTimeForgotPin"
                )
            }
        }
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(pin.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun setupBiometricAuthentication() {
        executor = ContextCompat.getMainExecutor(this)

        biometricPrompt =
            BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    startActivity(Intent(this@EnterPinActivity, LockedImagesActivity::class.java))
                    nextScreenAnimation()
                    finish()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    val failedAttempts = ePreferences.getInt("fingerprint_failed_attempts", 0) + 1
                    ePreferences.putInt("fingerprint_failed_attempts", failedAttempts)

                    if (failedAttempts >= 10) {
                        Toast.makeText(
                            this@EnterPinActivity,
                            getString(R.string.too_many_failed_attempts_please_use_pin),
                            Toast.LENGTH_LONG
                        ).show()
                        binding.tvUseFingerPrint.visibility = View.GONE
                        Handler(Looper.getMainLooper()).postDelayed({
                            ePreferences.putInt("fingerprint_failed_attempts", 0)
                            if (ePreferences.getBoolean("fingerprint_enabled", false)) {
                                binding.tvUseFingerPrint.visibility = View.VISIBLE
                            }
                        }, 30000)
                    } else {
                        Toast.makeText(
                            this@EnterPinActivity,
                            getString(R.string.authentication_failed_try_again), Toast.LENGTH_SHORT
                        ).show()
                        binding.tvUseFingerPrint.visibility = View.VISIBLE
                    }
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.unlock_with_fingerprint))
            .setSubtitle(getString(R.string.touch_the_fingerprint_sensor))
            .setNegativeButtonText(getString(R.string.use_pin))
            .build()

        val biometricManager = BiometricManager.from(this)
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                if (ePreferences.getBoolean("fingerprint_enabled", false)) {
                    binding.tvUseFingerPrint.visibility = View.VISIBLE
                    biometricPrompt.authenticate(promptInfo)
                } else {
                    binding.tvUseFingerPrint.visibility = View.GONE
                }
            }

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                binding.tvUseFingerPrint.visibility = View.GONE
            }
        }
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, EnterPinActivity::class.java)
            context.startActivity(intent)
            (context as Activity).overridePendingTransition(
                R.anim.in_right,
                R.anim.out_left
            )
        }
    }
}