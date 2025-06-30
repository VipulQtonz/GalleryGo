package com.photogallery.activity

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.ImageView
import androidx.core.net.toUri
import com.photogallery.MyApplication
import com.photogallery.R
import com.photogallery.databinding.ActivityStartBinding
import kotlin.random.Random

class StartActivity : BaseActivity<ActivityStartBinding>() {
    private val handler = Handler(Looper.getMainLooper())
    private val imageViews = mutableListOf<ImageView>()

    private val primaryImageResources = listOf(
        R.drawable.image1,
        R.drawable.image2,
        R.drawable.image3,
        R.drawable.image4,
        R.drawable.image5,
        R.drawable.image6,
        R.drawable.image7,
        R.drawable.image8,
        R.drawable.image9,
        R.drawable.image10,
        R.drawable.image11
    )

    override fun getViewBinding(): ActivityStartBinding {
        return ActivityStartBinding.inflate(layoutInflater)
    }

    override fun init(savedInstanceState: Bundle?) {
        onClickPolicy()
        initializeImageViews()
        assignInitialImages()
        startAnimation()
    }

    override fun addListener() {
        binding.btnStart.setOnClickListener {
            handler.removeCallbacksAndMessages(null)
            startActivity(Intent(this@StartActivity, PermissionActivity::class.java))
            nextScreenAnimation()
            finish()
        }
    }

    override fun onBackPressedDispatcher() {
        handler.removeCallbacksAndMessages(null)
        backScreenAnimation()
        finish()
    }

    private fun initializeImageViews() {
        imageViews.clear()
        imageViews.addAll(
            listOf(
                binding.img1,
                binding.img2,
                binding.img3,
                binding.img4,
                binding.img5,
                binding.img6,
                binding.img8,
                binding.img9,
                binding.img10,
                binding.img11,
                binding.img12
            )
        )
    }

    private fun assignInitialImages() {
        val shuffledImages = primaryImageResources.shuffled()
        for (i in imageViews.indices) {
            val imageRes = shuffledImages[i % shuffledImages.size]
            imageViews[i].setImageResource(imageRes)
        }
    }

    private fun startAnimation() {
        if (imageViews.size < 2) return

        var index1: Int
        var index2: Int
        do {
            index1 = Random.nextInt(imageViews.size)
            index2 = Random.nextInt(imageViews.size)
        } while (index1 == index2)

        val imageView1 = imageViews[index1]
        val imageView2 = imageViews[index2]

        val drawable1 = imageView1.drawable
        val drawable2 = imageView2.drawable

        val fadeToHalf = AlphaAnimation(1f, 0.2f).apply {
            duration = 1000
            fillAfter = true
            setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}
                override fun onAnimationRepeat(animation: Animation?) {}

                override fun onAnimationEnd(animation: Animation?) {
                    imageView1.setImageDrawable(drawable2)
                    imageView2.setImageDrawable(drawable1)

                    val fadeBack = AlphaAnimation(0.2f, 1f).apply {
                        duration = 1000
                        fillAfter = true
                    }
                    imageView1.startAnimation(fadeBack)
                    imageView2.startAnimation(fadeBack)
                }
            })
        }

        imageView1.startAnimation(fadeToHalf)
        imageView2.startAnimation(fadeToHalf)
        handler.postDelayed({ startAnimation() }, 2500)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun onClickPolicy() {
        val termsAndPrivacy = getString(R.string.get_started_content)
        val privacyPolicyText = getString(R.string.privacy_policy)
        val spannableString = SpannableString(termsAndPrivacy)

        val privacyStart = termsAndPrivacy.indexOf(privacyPolicyText)
        val privacyEnd = privacyStart + privacyPolicyText.length

        if (privacyStart != -1) {
            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    val url = MyApplication.PRIVACY_POLICY_LINK.toUri()
                    val intent = Intent(Intent.ACTION_VIEW, url)
                    widget.context.startActivity(intent)
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.isUnderlineText = false
                    ds.color = getAttributeColor(R.attr.colorApp)
                }
            }

            spannableString.setSpan(
                clickableSpan, privacyStart, privacyEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        binding.tvPrivacyPolicy.text = spannableString
        binding.tvPrivacyPolicy.movementMethod = LinkMovementMethod.getInstance()
        binding.tvPrivacyPolicy.highlightColor = Color.TRANSPARENT
    }
}