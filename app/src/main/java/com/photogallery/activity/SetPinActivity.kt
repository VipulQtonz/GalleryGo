package com.photogallery.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckedTextView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatEditText
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.photogallery.MyApplication.Companion.setupTooltip
import com.photogallery.R
import com.photogallery.base.BaseActivity
import com.photogallery.databinding.ActivitySetPinBinding
import com.skydoves.balloon.ArrowOrientation
import org.json.JSONObject
import java.security.MessageDigest

class SetPinActivity : BaseActivity<ActivitySetPinBinding>() {

    private lateinit var tvPrompt: TextView
    private lateinit var ivDigitOne: ImageView
    private lateinit var ivDigitTwo: ImageView
    private lateinit var ivDigitThree: ImageView
    private lateinit var ivDigitFour: ImageView
    private lateinit var spinnerQuestion: Spinner
    private lateinit var etAnswer: AppCompatEditText
    private lateinit var btnNext: Button
    private lateinit var securityQuestionLayout: LinearLayout
    private lateinit var llKeyboard: CardView
    private var currentStep = 1
    private var pin = ""
    private var originalPin = ""
    private var recoveryEmail = ""
    private var confirmRecoveryEmail = ""
    private val securityQuestions = mutableMapOf<String, String>()
    private var questionCount = 0
    private var isCompleteSetup = false

    override fun getViewBinding(): ActivitySetPinBinding {
        return ActivitySetPinBinding.inflate(layoutInflater)
    }

    override fun init(savedInstanceState: Bundle?) {
        tvPrompt = binding.tvPrompt
        ivDigitOne = binding.ivDigitOne
        ivDigitTwo = binding.ivDigitTwo
        ivDigitThree = binding.ivDigitThree
        ivDigitFour = binding.ivDigitFour
        spinnerQuestion = binding.spinnerQuestion
        etAnswer = binding.etAnswer
        btnNext = binding.btnNext
        securityQuestionLayout = binding.securityQuestionLayout
        llKeyboard = binding.llKeyboard

        setupSpinner()
        setupNumberPad()

        isCompleteSetup = intent.getBooleanExtra(EXTRA_COMPLETE_SETUP, false)
        currentStep = intent.getIntExtra(EXTRA_START_STEP, MODIFY_PIN_STEP)

        updateUIForStep()

        btnNext.setOnClickListener {
            handleNextStep()
        }
    }

    override fun addListener() {
        binding.toolbarSetPin.ivBack.setOnClickListener {
            onBackPressedDispatcher()
        }
    }

    override fun onBackPressedDispatcher() {
        if (currentStep > 1) {
            currentStep--
            if (currentStep == 1 || currentStep == 2) {
                pin = pin.dropLast(1)
                updatePinIndicators()
            } else if (currentStep == 3) {
                recoveryEmail = ""
            } else if (currentStep == 4) {
                confirmRecoveryEmail = ""
            } else if (currentStep == 5 && questionCount > 0) {
                questionCount--
                securityQuestions.remove(securityQuestions.keys.last())
            }
            updateUIForStep()
        } else {
            backScreenAnimation()
            finish()
        }
    }

    private fun setupNumberPad() {
        listOf(
            binding.btn0, binding.btn1, binding.btn2, binding.btn3, binding.btn4,
            binding.btn5, binding.btn6, binding.btn7, binding.btn8, binding.btn9
        ).forEach { button ->
            button.setOnClickListener {
                if (pin.length < 4) {
                    pin += (it as TextView).text
                    updatePinIndicators()
                }
            }
        }

        binding.btnBackspace.setOnClickListener {
            if (pin.isNotEmpty()) {
                pin = pin.dropLast(1)
                updatePinIndicators()
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

    private fun setupSpinner() {
        val questions = resources.getStringArray(R.array.security_questions)
        val adapter = object : ArrayAdapter<String>(
            this,
            R.layout.spinner_item,
            questions
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view =
                    convertView ?: layoutInflater.inflate(R.layout.spinner_item, parent, false)
                val textView = view as TextView
                textView.text = getItem(position)
                textView.setTextColor(getAttributeColor(R.attr.colorApp))
                return view
            }

            override fun getDropDownView(
                position: Int,
                convertView: View?,
                parent: ViewGroup
            ): View {
                val view = convertView ?: layoutInflater.inflate(
                    R.layout.spinner_dropdown_item,
                    parent,
                    false
                )
                val checkedTextView = view.findViewById<CheckedTextView>(android.R.id.text1)
                checkedTextView.text = getItem(position)
                val selectedColor = if (position == spinnerQuestion.selectedItemPosition) {
                    getAttributeColor(R.attr.colorApp)
                } else {
                    val typedValue = TypedValue()
                    context.theme.resolveAttribute(R.attr.colorTitleText, typedValue, true)
                    typedValue.data
                }
                checkedTextView.setTextColor(selectedColor)
                val divider = view.findViewById<View>(R.id.divider)
                divider.visibility = if (position == count - 1) View.GONE else View.VISIBLE
                return view
            }
        }
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinnerQuestion.adapter = adapter
        spinnerQuestion.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        spinnerQuestion.setPopupBackgroundResource(R.drawable.bg_rounded_corner)
    }

    private fun updateUIForStep() {
        btnNext.visibility = View.VISIBLE
        when (currentStep) {
            1 -> {
                llKeyboard.visibility = View.VISIBLE
                binding.llDigitTrack.visibility = View.VISIBLE
                securityQuestionLayout.visibility = View.GONE
                pin = ""
                updatePinIndicators()
                tvPrompt.text = getString(R.string.set_pin)
                binding.btnNext.text = getString(R.string.next)
                binding.toolbarSetPin.tvToolbarTitle.text = getString(R.string.set_pin)
            }

            2 -> {
                llKeyboard.visibility = View.VISIBLE
                binding.llDigitTrack.visibility = View.VISIBLE
                securityQuestionLayout.visibility = View.GONE
                pin = ""
                updatePinIndicators()
                tvPrompt.text = getString(R.string.confirm_pin)
                binding.btnNext.text = getString(R.string.next)
                binding.toolbarSetPin.tvToolbarTitle.text = getString(R.string.confirm_pin)
            }

            3 -> {
                llKeyboard.visibility = View.GONE
                binding.llDigitTrack.visibility = View.GONE
                securityQuestionLayout.visibility = View.VISIBLE
                binding.rlSpinnerQuestion.visibility = View.GONE
                etAnswer.setText("")
                etAnswer.hint = getString(R.string.enter_recovery_email_dot)
                etAnswer.inputType =
                    android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                showSystemKeyboard(binding.etAnswer)
                binding.tvSuggestionText.text =
                    getString(R.string.help_you_to_set_password_when_you_forget_it)
                binding.btnNext.text = getString(R.string.set_email)
                tvPrompt.text = getString(R.string.enter_recovery_email)
                binding.toolbarSetPin.tvToolbarTitle.text = getString(R.string.enter_recovery_email)
            }

            4 -> {
                llKeyboard.visibility = View.GONE
                binding.llDigitTrack.visibility = View.GONE
                securityQuestionLayout.visibility = View.VISIBLE
                binding.rlSpinnerQuestion.visibility = View.GONE
                etAnswer.setText("")
                etAnswer.hint = getString(R.string.re_enter_recovery_email)
                binding.tvSuggestionText.text =
                    getString(R.string.re_write_your_email_to_confirm_it)
                etAnswer.inputType =
                    android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                showSystemKeyboard(binding.etAnswer)
                binding.btnNext.text = getString(R.string.confirm_email)
                tvPrompt.text = getString(R.string.confirm_recovery_email)
                binding.toolbarSetPin.tvToolbarTitle.text =
                    getString(R.string.confirm_recovery_email)
            }

            5 -> {
                if (ePreferences.getBoolean("isFirstTimeSetLockViewMoreQuestionOptions", true)) {
                    setupTooltip(
                        this,
                        binding.rlSpinnerQuestion,
                        getString(R.string.tap_to_view_more_options),
                        ArrowOrientation.BOTTOM,
                        ePreferences,
                        "isFirstTimeSetLockViewMoreQuestionOptions"
                    )
                }
                llKeyboard.visibility = View.GONE
                binding.llDigitTrack.visibility = View.GONE
                securityQuestionLayout.visibility = View.VISIBLE
                binding.rlSpinnerQuestion.visibility = View.VISIBLE
                etAnswer.setText("")
                etAnswer.hint = getString(R.string.enter_the_answer)
                binding.tvSuggestionText.text =
                    getString(R.string.help_you_to_set_password_when_you_forget_it)
                etAnswer.inputType = android.text.InputType.TYPE_CLASS_TEXT
                showSystemKeyboard(binding.etAnswer)
                binding.btnNext.text = getString(R.string.save)
                tvPrompt.text = getString(R.string.set_security_question)
                binding.toolbarSetPin.tvToolbarTitle.text =
                    getString(R.string.set_security_question)
            }
        }
    }

    private fun showSystemKeyboard(view: View) {
        view.requestFocus()
        view.post {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun handleNextStep() {
        when (currentStep) {
            MODIFY_PIN_STEP -> {
                if (pin.length != 4) {
                    Toast.makeText(
                        this,
                        getString(R.string.pin_must_be_4_digits), Toast.LENGTH_SHORT
                    ).show()
                    return
                }
                originalPin = pin
                currentStep++
                updateUIForStep()
            }

            MODIFY_PIN_STEP + 1 -> {
                if (pin.length != 4) {
                    Toast.makeText(
                        this,
                        getString(R.string.pin_must_be_4_digits),
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }
                if (pin != originalPin) {
                    Toast.makeText(this, getString(R.string.pin_does_not_match), Toast.LENGTH_SHORT)
                        .show()
                    pin = ""
                    updatePinIndicators()
                    return
                }
                if (isCompleteSetup) {
                    currentStep++
                    updateUIForStep()
                } else {
                    saveData()
                    backScreenAnimation()
                    finish()
                }
            }

            CHANGE_EMAIL_STEP -> {
                recoveryEmail = etAnswer.text.toString().trim()
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(recoveryEmail).matches()) {
                    Toast.makeText(
                        this,
                        getString(R.string.please_enter_a_valid_email),
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }
                currentStep++
                updateUIForStep()
            }

            CHANGE_EMAIL_STEP + 1 -> {
                confirmRecoveryEmail = etAnswer.text.toString().trim()
                if (confirmRecoveryEmail != recoveryEmail) {
                    Toast.makeText(
                        this,
                        getString(R.string.emails_do_not_match),
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }
                if (isCompleteSetup) {
                    currentStep++
                    updateUIForStep()
                } else {
                    saveData()
                    backScreenAnimation()
                    finish()
                }
            }

            CHANGE_SECURITY_QUESTION_STEP -> {
                val question = spinnerQuestion.selectedItem.toString()
                val answer = etAnswer.text.toString().trim()
                if (answer.isEmpty()) {
                    Toast.makeText(
                        this,
                        getString(R.string.please_provide_an_answer),
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }
                securityQuestions[question] = answer
                questionCount++
                saveData()
                backScreenAnimation()
                finish()
            }
        }
    }

    private fun saveData() {
        // For complete setup, save all data at the end
        if (isCompleteSetup && currentStep == CHANGE_SECURITY_QUESTION_STEP) {
            ePreferences.putString("hashed_pin", hashPin(originalPin))
            ePreferences.putString("recovery_email", recoveryEmail)
            val questionsJson = JSONObject()
            for ((q, a) in securityQuestions) {
                questionsJson.put(q, a)
            }
            ePreferences.putString("security_questions", questionsJson.toString())
            ePreferences.putBoolean("PinSeated", true)
            Toast.makeText(this, getString(R.string.security_setup_completed), Toast.LENGTH_SHORT)
                .show()
        } else {
            // Individual flows
            when (currentStep) {
                in MODIFY_PIN_STEP..(MODIFY_PIN_STEP + 1) -> {
                    ePreferences.putString("hashed_pin", hashPin(originalPin))
                    ePreferences.putBoolean("PinSeated", true)
                    Toast.makeText(
                        this,
                        getString(R.string.pin_updated_successfully), Toast.LENGTH_SHORT
                    ).show()
                }

                in CHANGE_EMAIL_STEP..(CHANGE_EMAIL_STEP + 1) -> {
                    ePreferences.putString("recovery_email", recoveryEmail)
                    ePreferences.putBoolean("PinSeated", true)
                    Toast.makeText(
                        this,
                        getString(R.string.recovery_email_updated), Toast.LENGTH_SHORT
                    ).show()
                }

                CHANGE_SECURITY_QUESTION_STEP -> {
                    val questionsJson = JSONObject()
                    for ((q, a) in securityQuestions) {
                        questionsJson.put(q, a)
                    }
                    ePreferences.putString("security_questions", questionsJson.toString())
                    ePreferences.putBoolean("PinSeated", true)
                    Toast.makeText(
                        this,
                        getString(R.string.security_question_updated), Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(pin.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val EXTRA_START_STEP = "start_step"
        private const val EXTRA_COMPLETE_SETUP = "complete_setup"
        private const val MODIFY_PIN_STEP = 1
        private const val CHANGE_EMAIL_STEP = 3
        private const val CHANGE_SECURITY_QUESTION_STEP = 5

        fun start(context: Context) {
            val intent = Intent(context, SetPinActivity::class.java)
            context.startActivity(intent)
            (context as Activity).overridePendingTransition(
                R.anim.in_right,
                R.anim.out_left
            )
        }

        fun startForCompleteSetup(context: Context) {
            val intent = Intent(context, SetPinActivity::class.java).apply {
                putExtra(EXTRA_COMPLETE_SETUP, true)
                putExtra(EXTRA_START_STEP, MODIFY_PIN_STEP)
            }
            context.startActivity(intent)
            (context as Activity).overridePendingTransition(
                R.anim.in_right,
                R.anim.out_left
            )
        }

        fun startForModifyPin(context: Context) {
            startForIndividualModification(context, MODIFY_PIN_STEP)
        }

        fun startForChangeEmail(context: Context) {
            startForIndividualModification(context, CHANGE_EMAIL_STEP)
        }

        fun startForChangeSecurityQuestion(context: Context) {
            startForIndividualModification(context, CHANGE_SECURITY_QUESTION_STEP)
        }

        private fun startForIndividualModification(context: Context, step: Int) {
            val intent = Intent(context, SetPinActivity::class.java).apply {
                putExtra(EXTRA_COMPLETE_SETUP, false)
                putExtra(EXTRA_START_STEP, step)
            }
            context.startActivity(intent)
            (context as Activity).overridePendingTransition(
                R.anim.in_right,
                R.anim.out_left
            )
        }
    }
}