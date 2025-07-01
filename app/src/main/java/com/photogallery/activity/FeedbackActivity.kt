package com.photogallery.activity

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import com.google.firebase.firestore.FirebaseFirestore
import com.photogallery.R
import com.photogallery.base.BaseActivity
import com.photogallery.databinding.ActivityFeedbackBinding
import com.photogallery.utils.DeviceUtility
import com.photogallery.utils.DeviceUtility.checkNetworkConnectivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class FeedbackActivity : BaseActivity<ActivityFeedbackBinding>() {
    private lateinit var db: FirebaseFirestore
    var version: String = ""
    private var deviceModel: String = ""
    private var osVersion: String = ""

    override fun getViewBinding(): ActivityFeedbackBinding {
        return ActivityFeedbackBinding.inflate(layoutInflater)
    }

    override fun init(savedInstanceState: Bundle?) {
        binding.toolbarSettings.tvToolbarTitle.text = getString(R.string.feedback)
        db = FirebaseFirestore.getInstance()

        val pInfo = packageManager.getPackageInfo(packageName, 0)
        version = pInfo.versionName.toString()
        deviceModel = DeviceUtility.getDeviceName()
        osVersion = Build.VERSION.RELEASE
    }

    override fun addListener() {
        binding.toolbarSettings.ivBack.setOnClickListener {
            finish()
            backScreenAnimation()
        }
        binding.btnSubmit.setOnClickListener {
            if (checkNetworkConnectivity(this@FeedbackActivity)) {
                if (binding.edtRequirements.text.toString().trim().isEmpty()) {
                    Toast.makeText(
                        this@FeedbackActivity,
                        getString(R.string.please_enter_your_requirements), Toast.LENGTH_SHORT
                    ).show()
                } else {
                    showLoading()
                    val timeStamp: String = SimpleDateFormat(
                        "yyyy:MM:dd_HH:mm:ss",
                        Locale.US
                    ).format(Calendar.getInstance().time)

                    sendFeedBack(
                        version,
                        binding.edtRequirements.text.toString().trim(),
                        deviceModel,
                        timeStamp,
                        osVersion
                    )
                }
            } else {
                hideLoading()
                Toast.makeText(
                    this@FeedbackActivity,
                    getString(R.string.no_internet_connection),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun sendFeedBack(
        appVersion: String,
        requirements: String,
        deviceModel: String, date: String, osVersion: String
    ) {
        val feedback: MutableMap<String, Any> = HashMap()
        feedback["Date"] = date
        feedback["App Version"] = appVersion
        feedback["OS Version"] = osVersion
        feedback["Device Model"] = deviceModel
        feedback["Suggestions"] = requirements

        db.collection("Feedback").document(date + "_")
            .set(feedback)
            .addOnSuccessListener {
                Toast.makeText(
                    this@FeedbackActivity, getString(R.string.feedback_submit_successfully),
                    Toast.LENGTH_SHORT
                ).show()
                binding.edtRequirements.text.clear()
                hideLoading()
                finish()
                backScreenAnimation()
            }
            .addOnFailureListener { _ ->
                Toast.makeText(
                    this@FeedbackActivity,
                    getString(R.string.feedback_submit_error),
                    Toast.LENGTH_SHORT
                ).show()
                hideLoading()
            }
    }

    override fun onBackPressedDispatcher() {
        backScreenAnimation()
        finish()
    }
}