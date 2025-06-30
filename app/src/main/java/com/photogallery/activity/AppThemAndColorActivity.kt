package com.photogallery.activity

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.GridLayoutManager
import com.photogallery.MyApplication.Companion.setupTooltip
import com.photogallery.R
import com.photogallery.adapter.ThemeColorAdapter
import com.photogallery.databinding.ActivityAppThemAndColorBinding
import com.photogallery.utils.SharedPreferenceHelper.Companion.PREF_APP_COLOR
import com.photogallery.utils.SharedPreferenceHelper.Companion.PREF_THEME
import com.photogallery.utils.SharedPreferenceHelper.Companion.THEME_DARK
import com.photogallery.utils.SharedPreferenceHelper.Companion.THEME_LIGHT
import com.photogallery.utils.SharedPreferenceHelper.Companion.THEME_SYSTEM_DEFAULT
import com.skydoves.balloon.ArrowOrientation

class AppThemAndColorActivity : BaseActivity<ActivityAppThemAndColorBinding>() {
    private var selectedTheme = THEME_SYSTEM_DEFAULT
    private var selectedColorPos = 0

    override fun getViewBinding(): ActivityAppThemAndColorBinding {
        return ActivityAppThemAndColorBinding.inflate(layoutInflater)
    }

    override fun init(savedInstanceState: Bundle?) {
        applySavedTheme()
        binding.toolbarThemeAndColor.tvToolbarTitle.text = getString(R.string.app_theme_color)
        selectedTheme = ePreferences.getInt(PREF_THEME, THEME_SYSTEM_DEFAULT)
        selectedColorPos = ePreferences.getInt(PREF_APP_COLOR, 0)
        setupThemeSelection()
        setupRadioButtonGroup()
        setColorPickerAdapter()

        if (ePreferences.getBoolean("isFirstTimeThemeSelect", true)) {
            setupTooltip(
                this,
                binding.rlChooseTheme,
                getString(R.string.choose_your_favorite_theme),
                ArrowOrientation.TOP,
                ePreferences,
                "null"
            ) {
                setupTooltip(
                    this,
                    binding.rlChooseThemeColor,
                    getString(R.string.select_your_favorite_theme_color),
                    ArrowOrientation.TOP,
                    ePreferences,
                    "null"
                ) {
                    setupTooltip(
                        this,
                        binding.btnApplyNew,
                        getString(R.string.click_to_apply_changes),
                        ArrowOrientation.TOP,
                        ePreferences,
                        "isFirstTimeThemeSelect"
                    )
                }
            }
        }
    }

    private fun applySavedTheme() {
        val savedTheme = ePreferences.getInt(PREF_THEME, THEME_SYSTEM_DEFAULT)
        applyTheme(savedTheme, fromSystem = false)
    }

    private fun setupRadioButtonGroup() {
        val radioButtons = listOf(
            binding.rbLightTheme, binding.rbDarkTheme, binding.rbSystemDefaultTheme
        )

        radioButtons.forEach { radioButton ->
            radioButton.setOnClickListener {
                radioButtons.forEach { rb ->
                    rb.isChecked = rb == radioButton
                }

                selectedTheme = when (radioButton) {
                    binding.rbLightTheme -> THEME_LIGHT
                    binding.rbDarkTheme -> THEME_DARK
                    else -> THEME_SYSTEM_DEFAULT
                }
            }
        }
    }

    override fun addListener() {
        binding.toolbarThemeAndColor.ivBack.setOnClickListener { onBackPressedDispatcher() }
        binding.btnCancel.setOnClickListener { onBackPressedDispatcher() }
        binding.btnApplyNew.setOnClickListener {
            saveAndApplyTheme()
            restartApp()
        }
    }

    private fun setupThemeSelection() {
        binding.rbLightTheme.isChecked = selectedTheme == THEME_LIGHT
        binding.rbDarkTheme.isChecked = selectedTheme == THEME_DARK
        binding.rbSystemDefaultTheme.isChecked = selectedTheme == THEME_SYSTEM_DEFAULT
    }

    private fun saveAndApplyTheme() {
        ePreferences.putInt(PREF_THEME, selectedTheme)
        selectedColorPos.let {
            ePreferences.putInt(PREF_APP_COLOR, it)
        }
        applyTheme(selectedTheme, fromSystem = false)
        ePreferences.putInt(PREF_APP_COLOR, selectedColorPos)
    }

    private fun applyTheme(theme: Int, fromSystem: Boolean) {
        // Only allow system changes when System Default is selected AND it's a system change
        if (fromSystem && ePreferences.getInt(
                PREF_THEME, THEME_SYSTEM_DEFAULT
            ) != THEME_SYSTEM_DEFAULT
        ) {
            return
        }

        when (theme) {
            THEME_LIGHT -> {

                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                // Force light theme regardless of system
                delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_NO
            }

            THEME_DARK -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
            }

            else -> {
                // Follow system only for System Default option
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        }
    }

    private fun restartApp() {
        val intent = Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        nextScreenAnimation()
        finish()
    }

    private fun setColorPickerAdapter() {
        val rvColor = binding.rvThemeColor
        val layoutManager = GridLayoutManager(this, 4)
        rvColor.layoutManager = layoutManager
        rvColor.setHasFixedSize(true)
        val colorPickerAdapter = ThemeColorAdapter(this, selectedColorPos)
        colorPickerAdapter.setOnColorPickerClickListener(object :
            ThemeColorAdapter.OnThemeColorPickerClickListener {
            override fun onColorPickerClickListener(position: Int) {
                selectedColorPos = position
            }
        })
        rvColor.adapter = colorPickerAdapter
    }

    override fun onBackPressedDispatcher() {
        backScreenAnimation()
        finish()
    }
}