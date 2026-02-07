package com.agcforge.videodownloader.data.dto

import com.google.gson.annotations.SerializedName
<<<<<<< HEAD
import kotlinx.serialization.Serializable
=======
>>>>>>> d9441acea1800f24d98ca8ff996508019a679444

data class ProfileRequest (
    @SerializedName("full_name") val full_name: String,
    @SerializedName("email") val email: String,
)
<<<<<<< HEAD

@Serializable
data class FormReportError(
    val name: String,
    val email: String? = null,
    val url: String? = null,
    val platform: String? = null,
    val subject: String,
    override val message: String
) : Exception(message) {

    companion object {
        fun fromMap(map: Map<String, Any?>): FormReportError {
            return FormReportError(
                name = map["name"] as? String ?: "Unknown",
                email = map["email"] as? String?: "N/A",
                url = map["url"] as? String ?: "N/A",
                platform = map["platform"] as? String,
                subject = map["subject"] as? String ?: "No Subject",
                message = map["message"] as? String ?: "No Message"
            )
        }
    }
}
=======
>>>>>>> d9441acea1800f24d98ca8ff996508019a679444
