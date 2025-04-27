package com.example.taller2

import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.taller2.adapters.ContactosAdapter
import com.example.taller2.databinding.ActivityContactosBinding

class ContactosActivity : AppCompatActivity() {
    private lateinit var binding : ActivityContactosBinding
    private val contactos = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
        ActivityResultCallback {
            updateUI(it)
        }
    )

    private val projection = arrayOf(ContactsContract.Profile._ID, ContactsContract.Profile.DISPLAY_NAME_PRIMARY)
    private lateinit var adapter : ContactosAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactosBinding.inflate(layoutInflater)
        setContentView(binding.root)
        adapter = ContactosAdapter(this, null, 0)


        if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_DENIED){
            if(shouldShowRequestPermissionRationale(android.Manifest.permission.READ_CONTACTS)){
                Toast.makeText(this, "Se requiere el permiso para mostrar los contactos", Toast.LENGTH_LONG).show()
            }
            contactos.launch(android.Manifest.permission.READ_CONTACTS)
        }else{
            binding.listContactos.adapter = adapter
            updateUI(true)
        }
    }

    private fun updateUI(permission : Boolean){
        if(permission){
            //Permission Granted
            var cursor = contentResolver.query(ContactsContract.Contacts.CONTENT_URI, projection, null, null, null)
            adapter.changeCursor(cursor)
        }else{
            Toast.makeText(this, "No se tiene permiso para acceder a los contactos", Toast.LENGTH_LONG).show()
        }
    }
}