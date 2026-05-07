package com.example.cleancity.markers

import com.example.cleancity.shared.models.ComplaintResponse
import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.shared.models.MapMarkersResponse
import com.example.cleancity.shared.models.ProblemCategory
import com.example.cleancity.shared.requests.CreateComplaintRequest
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
            category = request.category.name,
            description = request.description,
            photoPath = savedPath,
            latitude = request.latitude,
            longitude = request.longitude,
            address = request.address,
            deviceId = request.deviceId
        )

        return row.toComplaintResponse()
    }

    fun getAllMarkers(): MapMarkersResponse {
        val complaints = repository.getAllComplaints().map { it.toComplaintResponse() }
        return MapMarkersResponse(complaints)
    }

    fun getMarkersInBounds(swLat: Double, swLon: Double, neLat: Double, neLon: Double): MapMarkersResponse {
        val complaints = repository.getComplaintsInBounds(swLat, swLon, neLat, neLon).map { it.toComplaintResponse() }
        return MapMarkersResponse(complaints)
    }

    fun getComplaintById(id: Long): ComplaintResponse? {
        return repository.getComplaintById(id)?.toComplaintResponse()
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
        category = ProblemCategory.valueOf(category),
        description = description,
        photoUrl = storage.getUrl(photoPath),
        latitude = latitude,
        longitude = longitude,
        address = address,
        status = ComplaintStatus.valueOf(status),
        createdAt = createdAt.toString()
    )
}
