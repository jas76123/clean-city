package com.example.cleancity.markers

import com.example.cleancity.shared.models.*
import com.example.cleancity.shared.requests.CreateComplaintRequest
import com.example.cleancity.shared.requests.CreateSubbotnikRequest
import com.example.cleancity.storage.StorageService

class MarkerService(
    private val repository: MarkerRepository,
    private val storage: StorageService
) {
    companion object {
        private const val MAX_PHOTO_SIZE = 10 * 1024 * 1024 // 10 MB
        private val ALLOWED_EXTENSIONS = setOf("jpg", "jpeg", "png")
    }

    fun createComplaint(
        request: CreateComplaintRequest,
        photoBytes: ByteArray,
        photoFileName: String
    ): ComplaintResponse {
        validatePhoto(photoBytes, photoFileName)

        val savedPath = storage.save(photoFileName, photoBytes)
        val row = repository.createComplaint(
            type = request.type.name,
            description = request.description,
            photoPath = savedPath,
            latitude = request.latitude,
            longitude = request.longitude,
            address = request.address,
            deviceId = request.deviceId
        )

        return row.toComplaintResponse()
    }

    fun createSubbotnik(
        request: CreateSubbotnikRequest,
        photoBytes: ByteArray?,
        photoFileName: String?
    ): SubbotnikResponse {
        if (photoBytes != null && photoFileName != null) {
            validatePhoto(photoBytes, photoFileName)
        }

        val savedPath = if (photoBytes != null && photoFileName != null) {
            storage.save(photoFileName, photoBytes)
        } else null

        val row = repository.createSubbotnik(
            title = request.title,
            description = request.description,
            photoPath = savedPath,
            latitude = request.latitude,
            longitude = request.longitude,
            address = request.address,
            eventDate = request.date,
            eventTime = request.time,
            deviceId = request.deviceId
        )

        return row.toSubbotnikResponse()
    }

    fun getAllMarkers(): MapMarkersResponse {
        val complaints = repository.getAllComplaints().map { it.toComplaintResponse() }
        val subbotniks = repository.getAllSubbotniks().map { it.toSubbotnikResponse() }
        return MapMarkersResponse(complaints, subbotniks)
    }

    fun getComplaintById(id: Long): ComplaintResponse? {
        return repository.getComplaintById(id)?.toComplaintResponse()
    }

    fun getSubbotnikById(id: Long): SubbotnikResponse? {
        return repository.getSubbotnikById(id)?.toSubbotnikResponse()
    }

    private fun validatePhoto(bytes: ByteArray, fileName: String) {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        require(extension in ALLOWED_EXTENSIONS) {
            "Invalid file format: $extension. Allowed: ${ALLOWED_EXTENSIONS.joinToString()}"
        }
        require(bytes.size <= MAX_PHOTO_SIZE) {
            "File too large: ${bytes.size} bytes. Max: $MAX_PHOTO_SIZE bytes"
        }
    }

    private fun ComplaintRow.toComplaintResponse() = ComplaintResponse(
        id = id,
        type = ProblemType.valueOf(type),
        description = description,
        photoUrl = storage.getUrl(photoPath),
        latitude = latitude,
        longitude = longitude,
        address = address,
        status = MarkerStatus.valueOf(status),
        createdAt = createdAt.toString()
    )

    private fun SubbotnikRow.toSubbotnikResponse() = SubbotnikResponse(
        id = id,
        title = title,
        description = description,
        photoUrl = photoPath?.let { storage.getUrl(it) },
        date = eventDate,
        time = eventTime,
        latitude = latitude,
        longitude = longitude,
        address = address,
        createdAt = createdAt.toString()
    )
}
