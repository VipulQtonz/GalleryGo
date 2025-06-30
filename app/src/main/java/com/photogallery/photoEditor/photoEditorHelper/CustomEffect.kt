package com.photogallery.photoEditor.photoEditorHelper

import android.text.TextUtils

class CustomEffect private constructor(builder: Builder) {
    val effectName: String = builder.mEffectName
    val parameters: Map<String, Any>
    class Builder(effectName: String) {
        val mEffectName: String
        val parametersMap: MutableMap<String, Any> = HashMap()

        fun setParameter(paramKey: String, paramValue: Any): Builder {
            parametersMap[paramKey] = paramValue
            return this
        }

        fun build(): CustomEffect {
            return CustomEffect(this)
        }

        init {
            if (TextUtils.isEmpty(effectName)) {
                throw RuntimeException("Effect name cannot be empty.Please provide effect name from EffectFactory")
            }
            mEffectName = effectName
        }
    }

    init {
        parameters = builder.parametersMap
    }
}