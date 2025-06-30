package com.photogallery.dialog

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.view.LayoutInflater
import androidx.core.graphics.drawable.toDrawable
import com.photogallery.R
import com.photogallery.databinding.ExitFromAppCustomDialogBinding

class ExitAppCustomDialog(
    isFromRateUse: Boolean,
    private val context: Activity,
    private val onYesClick: (() -> Unit)? = null
) {
    private val dialogBinding: ExitFromAppCustomDialogBinding =
        ExitFromAppCustomDialogBinding.inflate(LayoutInflater.from(context))
    private val builder: AlertDialog.Builder = AlertDialog.Builder(context)
    private val alertDialog: AlertDialog

    init {
        if (isFromRateUse) {
            dialogBinding.btnRateUs.text = context.getString(R.string.rate_us)
        } else {
            dialogBinding.btnExit.text = context.getString(R.string.yes)
        }
        builder.setView(dialogBinding.root)
        alertDialog = builder.create()
        alertDialog.setCancelable(true)
        alertDialog.setCanceledOnTouchOutside(true)
        alertDialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        setClickActions()
    }

    private fun setClickActions() {
        dialogBinding.btnExit.setOnClickListener {
            context.finishAffinity()
            alertDialog.dismiss()
        }

        dialogBinding.btnRateUs.setOnClickListener {
            onYesClick?.invoke()
            alertDialog.dismiss()
        }

        dialogBinding.ivCancel.setOnClickListener {
            alertDialog.dismiss()
        }
    }

    fun show() {
        alertDialog.show()
    }
}
