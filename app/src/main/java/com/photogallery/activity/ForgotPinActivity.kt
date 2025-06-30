package com.photogallery.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.photogallery.R
import com.photogallery.databinding.ActivityForgotPinBinding
import org.json.JSONObject

class ForgotPinActivity : BaseActivity<ActivityForgotPinBinding>() {
    private lateinit var etRecoveryEmail: EditText
    private lateinit var tvQuestion: TextView
    private lateinit var etAnswer: EditText
    private lateinit var btnVerify: Button
    private lateinit var btnResetPin: Button

    private var securityQuestions: Map<String, String>? = null
    private var question: String? = null

    override fun getViewBinding(): ActivityForgotPinBinding {
        return ActivityForgotPinBinding.inflate(layoutInflater)
    }

    override fun init(savedInstanceState: Bundle?) {
        // Initialize views
        etRecoveryEmail = binding.etRecoveryEmail
        tvQuestion = binding.tvQuestion1
        etAnswer = binding.etAnswer1
        btnVerify = binding.btnVerify
        btnResetPin = binding.btnResetPin

        // Hide unused question fields
        binding.tvQuestion2.visibility = View.GONE
        binding.etAnswer2.visibility = View.GONE
        binding.tvQuestion3.visibility = View.GONE
        binding.etAnswer3.visibility = View.GONE

        binding.toolbarForgotPin.tvToolbarTitle.text =
            getString(R.string.forgot_pin)

        setSecurityQuestionVisibility(View.GONE)

        btnVerify.setOnClickListener {
            if (securityQuestions == null) {
                fetchSecurityQuestions()
            } else {
                verifyAnswer()
            }
        }

        btnResetPin.setOnClickListener {
            startActivity(Intent(this, SetPinActivity::class.java))
            nextScreenAnimation()
            finish()
        }
    }

    override fun addListener() {
        binding.toolbarForgotPin.ivBack.setOnClickListener {
            onBackPressedDispatcher()
        }
    }

    override fun onBackPressedDispatcher() {
        backScreenAnimation()
        finish()
    }

    private fun fetchSecurityQuestions() {
        val email = etRecoveryEmail.text.toString().trim()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, getString(R.string.please_enter_a_valid_email), Toast.LENGTH_SHORT).show()
            return
        }

        val storedEmail = ePreferences.getString("recovery_email", "")
        if (storedEmail == email) {
            val questionsJson = ePreferences.getString("security_questions", "")
            if (questionsJson!!.isNotEmpty()) {
                securityQuestions = JSONObject(questionsJson).toMap()
                if (securityQuestions != null && securityQuestions!!.isNotEmpty()) {
                    question = securityQuestions!!.keys.first()
                    tvQuestion.text = question
                    setSecurityQuestionVisibility(View.VISIBLE)
                    btnVerify.text = getString(R.string.submit_answer)
                } else {
                    Toast.makeText(this,
                        getString(R.string.no_security_question_found), Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, getString(R.string.no_security_question_found), Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, getString(R.string.email_not_found), Toast.LENGTH_SHORT).show()
        }
    }

    private fun verifyAnswer() {
        val answer = etAnswer.text.toString().trim()
        if (answer.isEmpty()) {
            Toast.makeText(this, getString(R.string.please_provide_an_answer), Toast.LENGTH_SHORT).show()
            return
        }

        securityQuestions?.let { questions ->
            if (questions[question] == answer) {
                Toast.makeText(this, getString(R.string.verification_successful), Toast.LENGTH_SHORT).show()
                btnResetPin.visibility = View.VISIBLE
                btnVerify.visibility = View.GONE
                setSecurityQuestionVisibility(View.GONE)
                etRecoveryEmail.isEnabled = false
            } else {
                Toast.makeText(this, getString(R.string.incorrect_answer), Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(this, getString(R.string.no_security_question_loaded), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setSecurityQuestionVisibility(visibility: Int) {
        tvQuestion.visibility = visibility
        etAnswer.visibility = visibility
    }

    // Helper function to convert JSONObject to Map
    private fun JSONObject.toMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (key in keys()) {
            map[key] = getString(key)
        }
        return map
    }

    companion object {
        fun start(context: Context, recoveryEmail: String? = null) {
            val intent = Intent(context, ForgotPinActivity::class.java)
            recoveryEmail?.let { intent.putExtra("recoveryEmail", it) }
            context.startActivity(intent)
            (context as Activity).overridePendingTransition(
                R.anim.in_right,
                R.anim.out_left
            )
        }
    }
}