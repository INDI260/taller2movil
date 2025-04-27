package com.example.taller2.adapters

import android.content.Context
import android.database.Cursor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.cursoradapter.widget.CursorAdapter
import com.example.taller2.databinding.ContactoBinding

class ContactosAdapter(context: Context?, c: Cursor?, flags: Int) :
    CursorAdapter(context, c, flags) {
    private lateinit var binding: ContactoBinding
    override fun newView(context: Context?, cursor: Cursor?, parent: ViewGroup?): View {
        binding = ContactoBinding.inflate(LayoutInflater.from(context))
        return binding.contacto
    }

    override fun bindView(view: View?, context: Context?, cursor: Cursor?) {
        binding.idContacto.text = cursor?.getInt(0).toString()
        binding.nombreContacto.text = cursor?.getString(1)
    }
}