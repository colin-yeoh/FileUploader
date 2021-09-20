package com.cyapp.fileuploader

import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.cyapp.fileuploader.databinding.FragmentFirstBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.*

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private val fileLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            lifecycleScope.launchWhenCreated {
                runCatching {
                    val file = createImageFile(uri)
                    FileUploader.Builder()
                        .serverUrl("your server url")
                        .headers(
                            "custom-header1","custom-header-value1",
                            "custom-header2","custom-header-value2",
                            "custom-header3","custom-header-value3",
                            "custom-header4","custom-header-value4",
                        )
                        .entityForm(
                            mapOf(
                                "key1" to "value1",
                                "key2" to "value2",
                                "key3" to "value3",
                            )
                        )
                        .partParameters("part-kay", file.name, "image/*")
                        .build()
                        .uploadFile(file)
                        .flowOn(Dispatchers.IO)
                        .collect { state ->
                            withContext(Dispatchers.Main) {
                                binding.textviewFirst.text = "$state"
                            }
                        }
                }.onFailure {
                    Log.d("TAG", "$it")
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonUpload.setOnClickListener {
            fileLauncher.launch("image/*")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    @Throws(IOException::class)
    private fun createImageFile(uri: Uri): File {
        val imageFileName = "JPEG_" + System.currentTimeMillis() + "_"
        val storageDir = requireActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val file = File.createTempFile(
            imageFileName,  /* prefix */
            ".jpg",  /* suffix */
            storageDir /* directory */
        )

        val inputStream = requireActivity().contentResolver.openInputStream(uri)
        val fileOutputStream = FileOutputStream(file)
        // Copying
        // Copying
        copyStream(inputStream!!, fileOutputStream)
        fileOutputStream.close()
        inputStream.close()
        return file
    }

    @Throws(IOException::class)
    fun copyStream(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(1024)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
        }
    }
}