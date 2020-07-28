package com.example.appthermometerfrag


data class ArduinoWifiDevice(var dummy:Int = 0) {
    companion object {
        const val APDefaultSSID = "8266_THERMOMETER"
        const val APDefaultPasswd = "nana12345"
        const val APDefaultIP = "192.168.4.1"
        const val APDefaultPort = 81
//        var macAP: String? = null
        var macDevice: String = ""
        var ipDevice: String? = null
    }
}
