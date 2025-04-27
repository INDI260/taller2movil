package com.example.taller2.entities

import org.json.JSONObject
import java.util.Date

class MyLocation(private val date : Date, private val latitude: Double, private val longitude: Double){
    fun toJSON() : JSONObject {
        val obj = JSONObject();
        obj.put("latitude", latitude)
        obj.put("longitude", longitude)
        obj.put("date", date)
        return obj
    }
}