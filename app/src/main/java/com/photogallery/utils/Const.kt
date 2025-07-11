package com.photogallery.utils

import android.graphics.Color

object Const {
    const val TYPE_HEADER = 0
    const val TYPE_IMAGE = 1

    const val TYPE_FACE_GROUP = 0
    const val TYPE_OTHER_GROUP = 1

    const val PINCH_TEXT_SCALABLE_INTENT_KEY = "PINCH_TEXT_SCALABLE"
    const val REQUEST_CROP_IMAGE = 101

    const val REQUEST_SELECT_PICTURE_FOR_FRAGMENT = 0x02

    const val DEFAULT_ERASER_SIZE = 50.0f

    const val PRESSURE_THRESHOLD = 0.67f

    const val IMG_SRC_ID = 1
    const val SHAPE_SRC_ID = 2
    const val GL_FILTER_ID = 3

    const val PERMISSION_REQUEST_CODE = 1001

    const val SWIPE_THRESHOLD = 200
    const val SWIPE_VELOCITY_THRESHOLD = 400

    const val TOUCH_TOLERANCE = 4f

    const val DEFAULT_SHAPE_SIZE = 25.0f
    val DEFAULT_SHAPE_OPACITY = null
    const val DEFAULT_SHAPE_COLOR = Color.BLACK

    const val THEME_LIGHT = 0
    const val THEME_DARK = 1
    const val THEME_SYSTEM_DEFAULT = 2

    const val SPAN_COUNT_DEFAULT = 3
}