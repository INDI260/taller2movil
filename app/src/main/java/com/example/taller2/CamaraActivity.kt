package com.example.taller2

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.taller2.databinding.ActivityCamaraBinding
import java.io.File

class CamaraActivity : AppCompatActivity() {

    private lateinit var binding : ActivityCamaraBinding
    private lateinit var imagenUri : Uri

    private val getContentGallery = registerForActivityResult(ActivityResultContracts.GetContent(),
        ActivityResultCallback {
        loadImage(it!!)
    })

    private val getContentCamera = registerForActivityResult(
        ActivityResultContracts.TakePicture(),ActivityResultCallback {
            if(it){
                loadImage(imagenUri)
            }
        })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCamaraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.gallery.setOnClickListener {
            getContentGallery.launch("image/*")
        }

        binding.camera.setOnClickListener {
            val file = File(filesDir, "picFromCamera");
            imagenUri = FileProvider.getUriForFile(
                baseContext,
                baseContext.
                packageName + ".fileprovider", file)
            getContentCamera.launch(imagenUri);

        }
    }

    private fun loadImage(uri : Uri){
        val imageStream = contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(imageStream)
        binding.foto.setImageBitmap(bitmap)
    }



}