package com.cyapp.fileuploader

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.internal.closeQuietly
import okio.BufferedSink
import okio.Source
import okio.source
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer

class FileUploader private constructor(
    private var headersNameAndValues: Array<out String>,
    private var serverUrl: String,
    private var entityForm: Map<String, Any>,
    private var partParams: PartParams
) {

    data class Builder(
        private var headersNameAndValues: Array<out String> = arrayOf(),
        private var serverUrl: String = "",
        private var entityForm: Map<String, Any> = mapOf(),
        private var partParams: PartParams = PartParams( "", "", "")
    ) {
        fun headers(vararg namesAndValues: String) = apply { headersNameAndValues = namesAndValues }
        fun serverUrl(value: String) = apply { serverUrl = value }
        fun entityForm(value: Map<String, String>) = apply { entityForm = value }
        fun partParameters(keyName: String, fileName: String, mimeType: String) = apply {
            partParams = PartParams(keyName, fileName, mimeType)
        }
        fun build(): FileUploader = FileUploader(
            headersNameAndValues,
            serverUrl,
            entityForm,
            partParams
        )

        private suspend fun getBytesBuffer(inputStream: InputStream?): ByteBuffer {
            val initialStream: InputStream = ByteArrayInputStream(inputStream?.readBytes())
            return ByteBuffer.wrap(initialStream.readBytes())
        }
    }

    @ExperimentalCoroutinesApi
    suspend fun uploadFile(file: File, withProgress: Boolean = true): Flow<UploadState> = callbackFlow {

        val listener = object : ProgressRequestBody.ProgressListener {
            override fun transferred(percent: Int) {
                trySend(UploadState.Progress(percent))
            }
        }

        try {
            trySend(UploadState.Started)

            val client = OkHttpClient()

            val requestBodyBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)

            entityForm.forEach { (key, value) ->
                requestBodyBuilder.addFormDataPart(key, "$value")
            }

            val requestBody = if (withProgress) {
                ProgressRequestBody(
                    file,
                    partParams.mimeType,
                    listener
                )
            } else {
                file.asRequestBody("image/*".toMediaTypeOrNull())
            }

            requestBodyBuilder.addFormDataPart(
                partParams.partName,
                partParams.fileName,
                requestBody
            )

            val request = Request.Builder()
                .headers(Headers.headersOf(*headersNameAndValues))
                .url(serverUrl)
                .post(requestBodyBuilder.build())
                .build()

            val response: Response = client.newCall(request).execute()
            trySend(UploadState.Done(response.body?.string() ?: response.toString()))
        } catch (e: Exception) {
            trySend(UploadState.Failed(e))
        }

        awaitClose { close() }
    }


    data class PartParams(
        val partName: String,
        val fileName: String,
        val mimeType: String
    )

    sealed class UploadState {
        object Started : UploadState()
        data class Done(val result: String) : UploadState()
        data class Progress(val progress: Int) : UploadState()
        data class Failed(val e: Exception) : UploadState()
    }

    internal class ProgressRequestBody(
        private val file: File,
        private val contentType: String,
        private val listener: ProgressListener
    ) : RequestBody() {

        override fun contentLength(): Long {
            return file.length()
        }

        override fun contentType(): MediaType? {
            return contentType.toMediaTypeOrNull()
        }

        @Throws(IOException::class)
        override fun writeTo(sink: BufferedSink) {
            var source: Source? = null
            try {
                source = file.source()
                var total: Long = 0
                while (total < file.length()) {
                    val progress = (total / file.length().toFloat() * 100).toInt()
                    listener.transferred(progress)
                    source.read(sink.buffer, SEGMENT_SIZE)
                    sink.flush()
                    total += SEGMENT_SIZE
                }
            } finally {
                source?.closeQuietly()
            }
        }

        interface ProgressListener {
            fun transferred(percent: Int)
        }

        companion object {
            private const val SEGMENT_SIZE = 2048L // okio.Segment.SIZE
        }
    }
}