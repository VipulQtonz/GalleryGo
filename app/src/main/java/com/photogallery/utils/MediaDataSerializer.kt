package com.photogallery.utils

import android.net.Uri
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import com.photogallery.model.MediaData

object MediaDataSerializer {
    fun serialize(mediaList: List<MediaData>): String {
        val serializableList = mediaList.map { media ->
            mapOf(
                "id" to media.id,
                "name" to media.name,
                "path" to media.path,
                "uri" to media.uri.toString(),
                "dateTaken" to media.dateTaken,
                "isVideo" to media.isVideo,
                "duration" to media.duration,
                "daysRemaining" to media.daysRemaining,
                "isFavorite" to media.isFavorite
            )
        }
        return Gson().toJson(serializableList)
    }

    fun deserialize(json: String): List<MediaData> {
        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
        val mediaMaps = Gson().fromJson<List<Map<String, Any>>>(json, type)

        return mediaMaps.map { map ->
            MediaData(
                id = (map["id"] as Double).toLong(),
                name = map["name"] as String,
                path = map["path"] as String,
                uri = Uri.parse(map["uri"] as String),
                dateTaken = (map["dateTaken"] as Double).toLong(),
                isVideo = map["isVideo"] as Boolean,
                duration = (map["duration"] as Double).toLong(),
                daysRemaining = (map["daysRemaining"] as Double).toInt(),
                isFavorite = map["isFavorite"] as Boolean
            )
        }
    }
}