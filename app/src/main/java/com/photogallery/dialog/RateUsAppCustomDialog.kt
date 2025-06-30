package com.photogallery.dialog

import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.gms.tasks.Task
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.photogallery.MyApplication
import com.photogallery.R
import com.photogallery.databinding.RateUsAppCustomDialogBinding
import com.photogallery.utils.RatingBar
import com.photogallery.utils.SharedPreferenceHelper

class RateUsAppCustomDialog(private val mContext: Activity, private val isFromExit: Boolean) :
    Dialog(mContext) {
    private lateinit var binding: RateUsAppCustomDialogBinding
    private var ratingNew: Float = 0f
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.setBackgroundDrawableResource(R.color.transparent)
        binding = RateUsAppCustomDialogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        init()
        addListener()
    }

    private fun init() {
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun addListener() {
        binding.rbRating.setOnRatingBarChangeListener(object :
            RatingBar.OnRatingBarChangeListener {
            override fun onRatingChanged(
                ratingBarUtility: RatingBar?, rating: Float, fromUser: Boolean,
            ) {
                ratingNew = rating
            }
        })

        binding.btnRateUs.setOnClickListener {
            MyApplication.instance.pref.putBoolean(
                SharedPreferenceHelper.RATE_US,
                true
            )
            if ((ratingNew == 4f || ratingNew == 5f)) {
                Handler(Looper.getMainLooper()).postDelayed({
                    dismiss()
                    rateOurApp()
                    if (isFromExit) {
                        mContext.finishAffinity()
                    }
                }, 500)
            } else {
                Handler(Looper.getMainLooper()).postDelayed({
                    dismiss()
                    if (isFromExit) {
                        mContext.finishAffinity()
                    }
                }, 500)
                Toast.makeText(
                    context,
                    context.getString(R.string.thanks_for_rating),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        binding.tvLater.setOnClickListener {
            dismiss()
            if (isFromExit) {
                mContext.finishAffinity()
            }
        }

        binding.ivCancel.setOnClickListener {
            dismiss()
//            if (isFromExit) {
//                mContext.finishAffinity()
//            }
        }
    }

    private fun rateOurApp() {
        MyApplication.instance.pref.putBoolean(
            SharedPreferenceHelper.RATE_US,
            true
        )
        try {
            val manager: ReviewManager = ReviewManagerFactory.create(mContext)
            val request: Task<ReviewInfo> = manager.requestReviewFlow()
            request.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val reviewInfo: ReviewInfo = task.result
                    val flow: Task<Void> = manager.launchReviewFlow(mContext, reviewInfo)
                    flow.addOnSuccessListener {
                        Log.e("RateUs", "rateApp: ")
                    }
                } else {
                    Log.e("TagRating", task.isSuccessful.toString())
                }
            }
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                mContext, mContext.getString(R.string.google_play_not_installed),
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
