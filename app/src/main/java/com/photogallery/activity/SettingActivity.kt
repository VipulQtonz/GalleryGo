package com.photogallery.activity

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import com.photogallery.MyApplication
import com.photogallery.MyApplication.Companion.PRIVACY_POLICY_LINK
import com.photogallery.MyApplication.Companion.TERMS_OF_SERVICES_LINK
import com.photogallery.R
import com.photogallery.databinding.ActivitySettingBinding
import com.photogallery.databinding.DialogPersonaliseGridBinding
import com.photogallery.databinding.PermissionNotificationCustomDialogBinding
import com.photogallery.dialog.RateUsAppCustomDialog
import com.photogallery.utils.SharedPreferenceHelper

class SettingActivity : BaseActivity<ActivitySettingBinding>() {
    private val CODE_NOTIFICATION = 1235
    var personaliseLayoutDialog: AlertDialog? = null
    private var shortOrder = 0

    override fun getViewBinding(): ActivitySettingBinding {
        return ActivitySettingBinding.inflate(layoutInflater)
    }

    override fun init(savedInstanceState: Bundle?) {
        binding.switchNotification.isChecked =
            ePreferences.getBoolean(
                SharedPreferenceHelper.IS_NOTIFICATION_ON,
                false
            )
        binding.tvNotification.isSelected = true
        binding.tvRateUs.isSelected = true
        binding.tvFeedback.isSelected = true
        binding.tvShareApp.isSelected = true
        shortOrder = ePreferences.getInt("SortOrder", 0)
        binding.switchNotification.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (MyApplication.instance.isNotificationPermissionGranted()) {
                    binding.switchNotification.isChecked = true
                    ePreferences.putBoolean(
                        SharedPreferenceHelper.IS_NOTIFICATION_ON,
                        true
                    )
                } else {
                    notificationCustomDialog()
                    binding.switchNotification.isChecked = false
                    ePreferences.putBoolean(
                        SharedPreferenceHelper.IS_NOTIFICATION_ON,
                        false
                    )
                }
            } else {
                ePreferences.putBoolean(
                    SharedPreferenceHelper.IS_NOTIFICATION_ON,
                    false
                )
            }
        }
        binding.toolbarSettings.tvToolbarTitle.text = getString(R.string.settings)
    }

    override fun addListener() {
        binding.toolbarSettings.ivBack.setOnClickListener {
            onBackPressedDispatcher()
        }

        binding.rlRateUs.setOnClickListener {
            MyApplication.instance.preventTwoClick(it)
            if (!ePreferences.getBoolean(
                    SharedPreferenceHelper.RATE_US,
                    false
                )
            ) {
                RateUsAppCustomDialog(this, false).show()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.thanks_for_rating),
                    Toast.LENGTH_SHORT
                )
                    .show()
            }
        }

        binding.rlThemeAndColor.setOnClickListener {
            MyApplication.instance.preventTwoClick(it)
            val intent = Intent(this, AppThemAndColorActivity::class.java)
            startActivity(intent)
            nextScreenAnimation()
        }

        binding.rlFeedback.setOnClickListener {
            MyApplication.instance.preventTwoClick(it)
            val intent = Intent(this, FeedbackActivity::class.java)
            startActivity(intent)
            nextScreenAnimation()
        }

        binding.rlShareApp.setOnClickListener {
            MyApplication.instance.preventTwoClick(it)
            shareApp()
        }

        binding.rlPrivacyPolicy.setOnClickListener {
            MyApplication.instance.preventTwoClick(it)
            loadPrivacy(it)
        }

        binding.rlTermsOfService.setOnClickListener {
            MyApplication.instance.preventTwoClick(it)
            loadTermsOfService(it)
        }

        binding.rlSort.setOnClickListener {
            MyApplication.instance.preventTwoClick(it)
            showShortDialog(shortOrder)
        }
    }

    private fun loadPrivacy(view: View) {
        MyApplication.instance.preventTwoClick(view)
        startActivity(Intent(Intent.ACTION_VIEW, PRIVACY_POLICY_LINK.toUri()))
        nextScreenAnimation()
    }

    private fun loadTermsOfService(view: View) {
        MyApplication.instance.preventTwoClick(view)
        startActivity(Intent(Intent.ACTION_VIEW, TERMS_OF_SERVICES_LINK.toUri()))
        nextScreenAnimation()
    }

    private fun shareApp() {
        val appNameString = resources.getString(R.string.app_name)
        val sharingIntent = Intent("android.intent.action.SEND")
        sharingIntent.putExtra("android.intent.extra.SUBJECT", appNameString)
        sharingIntent.putExtra(
            Intent.EXTRA_TEXT, getString(R.string.app_name)
                    + getString(R.string.try_it_now) + "https://play.google.com/store/apps/details?id=" + this.applicationContext.packageName
        )
        sharingIntent.type = "text/plain"
        startActivity(Intent.createChooser(sharingIntent, getString(R.string.share_app)))
    }

    private fun notificationCustomDialog() {
        val dialogBinding =
            PermissionNotificationCustomDialogBinding.inflate(LayoutInflater.from(this))
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.requestWindowFeature(1)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        dialog.setContentView(dialogBinding.root)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()

        dialogBinding.btnAllow.setOnClickListener {
            MyApplication.instance.checkParm = true
            val settingsIntent: Intent =
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, CODE_NOTIFICATION)
            startActivityForResult(settingsIntent, CODE_NOTIFICATION)
            dialog.dismiss()
        }
        dialogBinding.tvDeny.setOnClickListener {
            dialog.dismiss()
        }
        dialogBinding.ivCancel.setOnClickListener {
            dialog.dismiss()
        }
    }

    private fun showShortDialog(order: Int) {
        val builder = AlertDialog.Builder(this)
        builder.setCancelable(false)
        val layoutDialogBinding = DialogPersonaliseGridBinding.inflate(LayoutInflater.from(this))
        builder.setCancelable(false)

        layoutDialogBinding.ivClose.visibility = View.GONE
        layoutDialogBinding.tvTitle.text = getString(R.string.sort)
        layoutDialogBinding.tvDay.text = getString(R.string.newest_at_top)
        layoutDialogBinding.ivDay.setImageResource(R.drawable.ic_newest_at_top)
        layoutDialogBinding.tvMonth.text = getString(R.string.newest_at_bottom)
        layoutDialogBinding.ivMonth.setImageResource(R.drawable.ic_newest_at_bottom)
        layoutDialogBinding.btnApply.visibility = View.GONE
        layoutDialogBinding.llApplyAndCancel.visibility = View.VISIBLE

        when (order) {
            0 -> {
                layoutDialogBinding.rbDay.isChecked = true
                layoutDialogBinding.rbComfort.isChecked = false
                layoutDialogBinding.rbMonth.isChecked = false
            }

            1 -> {
                layoutDialogBinding.rbMonth.isChecked = true
                layoutDialogBinding.rbDay.isChecked = false
                layoutDialogBinding.rbComfort.isChecked = false
            }
        }

        layoutDialogBinding.rbDay.setOnClickListener {
            layoutDialogBinding.rbDay.isChecked = true
            layoutDialogBinding.rbComfort.isChecked = false
            layoutDialogBinding.rbMonth.isChecked = false
            shortOrder = 0
        }

        layoutDialogBinding.rbMonth.setOnClickListener {
            layoutDialogBinding.rbMonth.isChecked = true
            layoutDialogBinding.rbComfort.isChecked = false
            layoutDialogBinding.rbDay.isChecked = false
            shortOrder = 1
        }

        layoutDialogBinding.btnApplyNew.setOnClickListener {
            ePreferences.putInt("SortOrder", shortOrder)
            MyApplication.isPhotoFetchReload = true
            MyApplication.isVideoFetchReload = true
            personaliseLayoutDialog?.dismiss()
        }
        layoutDialogBinding.btnCancel.setOnClickListener {
            personaliseLayoutDialog?.dismiss()
        }

        builder.setView(layoutDialogBinding.root)
        personaliseLayoutDialog = builder.create()
        personaliseLayoutDialog?.setCanceledOnTouchOutside(true)
        personaliseLayoutDialog?.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        personaliseLayoutDialog?.show()
    }

    private fun updateNotificationSwitchState() {
        val isNotificationEnabled = MyApplication.instance.isNotificationPermissionGranted()
        binding.switchNotification.isChecked = isNotificationEnabled
        ePreferences.putBoolean(SharedPreferenceHelper.IS_NOTIFICATION_ON, isNotificationEnabled)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CODE_NOTIFICATION) {
            updateNotificationSwitchState()
        }
    }

    override fun onResume() {
        super.onResume()
        updateNotificationSwitchState()
    }

    override fun onBackPressedDispatcher() {
        backScreenAnimation()
        finish()
    }
}