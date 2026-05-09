package com.example.cleancity.storage

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.net.URI

/**
 * Yandex Object Storage (S3-совместимое) — продакшен-имплементация.
 *
 * `endpoint` для Яндекса — `https://storage.yandexcloud.net`.
 * `publicUrlBase` — обычно `https://<bucket>.storage.yandexcloud.net`,
 * либо CDN-префикс если настроен.
 *
 * В Day 4 этот класс компилируется и тестируется, но используется только
 * при `app.stage=PROD` — в dev селектится [LocalStorageService].
 */
class S3StorageService(
    private val bucket: String,
    region: String,
    endpoint: String,
    accessKey: String,
    secretKey: String,
    private val publicUrlBase: String
) : StorageService {

    private val client: S3Client = S3Client.builder()
        .region(Region.of(region))
        .endpointOverride(URI.create(endpoint))
        .credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
        )
        .serviceConfiguration(
            // Yandex Object Storage не поддерживает virtual-host bucket-style
            // для произвольных bucket-имён — переключаемся на path-style.
            S3Configuration.builder().pathStyleAccessEnabled(true).build()
        )
        .build()

    override fun save(key: String, bytes: ByteArray, contentType: String): String {
        val request = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(contentType)
            .contentLength(bytes.size.toLong())
            .build()
        client.putObject(request, RequestBody.fromBytes(bytes))
        return urlFor(key)
    }

    override fun delete(key: String) {
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build())
        } catch (_: NoSuchKeyException) {
            // тихое игнорирование: повторный вызов delete должен быть идемпотентным
        }
    }

    override fun urlFor(key: String): String = "$publicUrlBase/$key"
}
