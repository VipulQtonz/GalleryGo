package com.photogallery.photoEditor.photoEditorHelper.shape

sealed interface ShapeType {
    object Brush : ShapeType
    object Oval : ShapeType
    object Rectangle : ShapeType
    object Line : ShapeType
    class Arrow(val pointerLocation: ArrowPointerLocation = ArrowPointerLocation.START) : ShapeType
}