@file:Suppress("DEPRECATION")

package com.example.appthermometerfrag

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.NETWORK_STATE_CHANGED_ACTION
import android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION
import android.os.AsyncTask
import android.os.StrictMode
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import timber.log.Timber
import java.io.*
import java.lang.Integer.max
import java.lang.Integer.min
import java.lang.Thread.sleep
import java.net.Socket
import java.net.UnknownHostException


@SuppressLint("StaticFieldLeak")
object WifiController  {

    private const val USB_SERIAL_REQUEST_INTERVAL = 30000L
    private const val USB_SERIAL_TIME_TO_CONNECT_INTERVAL = 10000L

    var mainActivity: AppCompatActivity? = null
    var appContext: Context? = null
    lateinit var wifiManager: WifiManager

    var newNetwork = ""
    var responseFromThermometer : String? = null

    var tabletAccessPointSSID : String = ""
    var tabletAccessPointPassword : String = ""

    interface WiFiOperationProgress {
        fun wifiOperationProgress(operation: WifiOperation, state:OperationState, progressPerc:Int, msg:String) : String?
        fun wifiNotificationEvent(event : WifiEvent, msg:String)
    }


    fun start(activity: AppCompatActivity, context: Context) {
        mainActivity = activity
        appContext = context
        wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

        val filter = IntentFilter()
        filter.addAction(NETWORK_STATE_CHANGED_ACTION)
        filter.addAction(SCAN_RESULTS_AVAILABLE_ACTION)

        context.registerReceiver(wifiEventsReceiver, filter)
    }


    fun openSocket(caller: WiFiOperationProgress, host: String, port: Int, timeout:Int=10_000) : Socket? {
        var osThread = OpenSocketTh(host, port)
        val startTime = System.currentTimeMillis()
        var tempoDecorrido = 0L
        var progress = (tempoDecorrido * 100 / timeout).toInt()
        var socket: Socket? = null
        var doingNow = String.format("OpenSocket %s:%d", host, port)
        var doingBefore = caller.wifiOperationProgress(WifiOperation.OPEN_SOCKET, OperationState.START, timeout,  doingNow) ?: ""

        try {
            osThread.start()
            while ( true ) {
                tempoDecorrido = System.currentTimeMillis() - startTime
                if ( tempoDecorrido >= timeout ) {
                    break
                }

                progress = (tempoDecorrido * 100 / timeout).toInt()
                progress = min(progress, 100)

                caller.wifiOperationProgress(WifiOperation.OPEN_SOCKET, OperationState.PROGRESS, progress, doingNow)

                Thread.sleep(100)
                if ( ! osThread.isAlive ) {
                    socket = osThread.s
                    break;
                }
            }
            osThread.interrupt()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        caller.wifiOperationProgress(WifiOperation.OPEN_SOCKET, OperationState.FINISH, progress, doingBefore)
        return socket
    }

    private class OpenSocketTh(val host: String, val port: Int) : Thread() {
        var s : Socket? = null
        var tenta=0

        override fun run() {
            while ( tenta++ < 10 ) {
                try {
                    Timber.e("WWWWWW Tentando(${tenta}) socket  host=${host}, porta=${port}")
                    sleep(100)
                    s = Socket(host, port)
                    if ( s?.isConnected() == true) {
                        Timber.i("Conectou Socket")
                        break
                    } else {
                        Timber.i("close Socket")
                        s?.close()
                        s=null
                    }
                } catch (e: UnknownHostException) {
                    Timber.e("UnknownHostException : ${e.message}")
                } catch (e: IOException) {
                    Timber.e("IOException : ${e.message}")
                } catch (e: SecurityException) {
                    Timber.e("SecurityException : ${e.message}")
                } catch (e: IllegalArgumentException) {
                    Timber.e("IllegalArgumentException : ${e.message}")
                } catch (e: Exception) {
                    Timber.e("Exception : ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }


    fun socketTransaction(caller: WiFiOperationProgress, socket: Socket, demanda: String, timeout:Int) : String? {
        var transaction = SocketTransactionTh(socket, demanda)
        val startTime = System.currentTimeMillis()
        var tempoDecorrido = 0L
        var progress = (tempoDecorrido * 100 / timeout).toInt()
        var transactionResponse : String? = null
        var doingNow = "Waiting Transaction..."

        var doingBefore = caller.wifiOperationProgress(WifiOperation.TRANSACTION, OperationState.START, timeout, doingNow) ?: ""

        try {
            transaction.start()
            while ( true ) {
                tempoDecorrido = System.currentTimeMillis() -  startTime
                if ( tempoDecorrido >= timeout ) {
                    break
                }

                progress = (tempoDecorrido * 100 / timeout).toInt()
                if ( progress > 100) progress = 100

                caller.wifiOperationProgress(WifiOperation.TRANSACTION, OperationState.PROGRESS, progress, doingNow)

                Thread.sleep(50)
                if ( ! transaction.isAlive ) {
                    transactionResponse = transaction.resposeFromServer
                    break;
                }
            }
            transaction.interrupt()
        } catch (e: IOException)
        {
            e.printStackTrace()
        }

        caller.wifiOperationProgress(WifiOperation.TRANSACTION, OperationState.FINISH, progress, doingBefore)
        return transactionResponse
    }

    private class SocketTransactionTh(val socket : Socket, val demanda: String) : Thread() {
        var resposeFromServer : String? = null
        override fun run() {
            try {
                if (socket.isConnected()) {
                    val out = PrintWriter(
                        BufferedWriter(OutputStreamWriter(socket.getOutputStream()) ), true)
                    val `in` = BufferedReader(InputStreamReader(socket.getInputStream()))
                    out.write(demanda)
                    out.flush()
                    Timber.i("Calling Write ${demanda}")
                    resposeFromServer = `in`.readLine()
                    out.close()
                    `in`.close()
                    Timber.i("response ======>>>>>  [${resposeFromServer}]  Tam: ${resposeFromServer?.length}")
                } else {
                    Timber.e( "Socket is not connected")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }





    fun isWifiAccessPointEnabled(): Boolean {
        val apState =
            wifiManager.javaClass.getMethod("getWifiApState").invoke(wifiManager) as Int

        val wifiConfig  = wifiManager.javaClass.getMethod("getWifiApConfiguration").invoke(wifiManager) as WifiConfiguration

        Timber.i("apState = ${apState}")

        val AP_STATE_ENABLED = 13

        if ( apState == AP_STATE_ENABLED ) {
            tabletAccessPointSSID = wifiConfig.SSID
            tabletAccessPointPassword = wifiConfig.preSharedKey
            Timber.i("ssid = ${tabletAccessPointSSID}   senha[${tabletAccessPointPassword}]")
        } else {
            tabletAccessPointSSID = ""
            tabletAccessPointPassword = ""
        }

        return (apState == AP_STATE_ENABLED)
    }

    fun getAccessPointSSID() : String {
        return(tabletAccessPointSSID)
    }

    fun getAccessPointPassword() : String {
        return(tabletAccessPointPassword)
    }

    fun isWifiConnected() : Boolean {
        val currentWifi: WifiInfo = wifiManager.getConnectionInfo()
        var currentSSID = currentWifi.getSSID()

        if ((currentSSID == null) || currentSSID.contains("unknown") || currentSSID.isEmpty()) {
            return false
        }
        return true
    }

    fun getCurrentSSID() : String {
        val currentWifi : WifiInfo = wifiManager.getConnectionInfo()
        var currentSSID = currentWifi.getSSID()

        if ( (currentSSID == null) || comparaSSID(currentSSID, "unknown" ) ) {
            currentSSID=""
        } else {
            // retira aspas inicial e final
            currentSSID = currentSSID.drop(1)
            currentSSID = currentSSID.dropLast(1)
        }
        Timber.i("Current SSID [${currentSSID}]")
        return currentSSID
    }

    fun isSSIDAvailable(ssid:String) : Boolean {
        val mScanResults: List<ScanResult> = wifiManager.getScanResults()
        var ssidLocalizado = false
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        Timber.i("Entrando em isSSIDAvailable")
        for ( item in mScanResults ) {
            Timber.i(item.SSID)
            if ( ssid == item.SSID)  {
                ssidLocalizado = true
            }
        }

        // Se não localizar, pede para fazer um novo Scan
        if ( ssidLocalizado == false) {
            wifiManager.startScan()
        }

        Timber.i("Saindo de isSSIDAvailable ssidLocalizado=[${ssidLocalizado}]")
        return ssidLocalizado
    }


    private val wifiEventsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            val networkInfo= intent.getParcelableExtra<NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)

            if ( networkInfo != null ) {
                Timber.i("wifiEventsReceiver recebendo uma notificacao de: action=${action}   isConnectedOrConnecting=${networkInfo.isConnectedOrConnecting}  state=${networkInfo.state}")

                if ( networkInfo.state == NetworkInfo.State.CONNECTED ) {
                    val ssid= intent.getParcelableExtra<WifiInfo>(WifiManager.EXTRA_WIFI_INFO)!!.ssid
                    val log="Connected to SSID:"+ssid
                    Timber.i("Connected to SSID:"+ssid)
                    Toast.makeText(context, log, Toast.LENGTH_SHORT).show()
                    newNetwork = ssid
                    ScreenLog.add(LogType.TO_HISTORY, "Connected to SSID:"+ssid)
                }

                if ( networkInfo.state == NetworkInfo.State.DISCONNECTED ) {
                    //                Toast.makeText(context, "Desconectando Wifi", Toast.LENGTH_SHORT).show()
                    Timber.i("NetworkInfo.State.DISCONNECTED ")
                    ScreenLog.add(LogType.TO_HISTORY, "Network Disconnected")
                    newNetwork = ""
                }
            }
        }
    }



    fun isConnectedTo(ssid: String):Boolean {
        Timber.i("isConnectedTo Antes [${wifiManager.connectionInfo.ssid}] [${ssid}]")
        return comparaSSID(wifiManager.connectionInfo.ssid, ssid)
    }


    fun getWiFiConfig(ssid: String): WifiConfiguration? {
        val wifiList= wifiManager.configuredNetworks

        Timber.i("getWiFiConfig   Procurando:[$ssid]")

        if ( wifiList != null) {
            if ( wifiList.size > 0 ) {
                // Nomes no vetor estão entra aspas
                for (item in wifiList){
                    if ( comparaSSID(item.SSID , ssid) ){
                        return item
                    }
                }
            }
        }
        return null
    }

    /**
     * Caso esteja conectado a uma rede WiFi, retorna o nome o SSID da rede.
     * retorna "" se não estiver conectado a nenhuma rede
     * retorna null se não conseguir desconectar.
     *
     */
    fun disconnectFromWiFiNetwork(caller : WiFiOperationProgress, tag:String ) : String? {
        val timeout = 3000
        val startTime = System.currentTimeMillis()
        var tempoDecorrido = 0L
        var progress = (tempoDecorrido * 100 / timeout).toInt()
        var disconnectedNetwork : String? = wifiManager.connectionInfo.ssid
        var returnValue : String? = null
        var doingBefore = ""

        if ( progress > 100) progress = 100


        Timber.i("disconnectFromWiFiNetwork from ${tag} SSID: ${wifiManager.connectionInfo.ssid}   networkId=${wifiManager.connectionInfo.networkId} ");

        if ( wifiManager.connectionInfo.networkId == -1 ) {
            Timber.i("<<<< Saindo de disconnectFromWiFiNetwork MEIO")
            return ""
        }


        try {
            newNetwork = wifiManager.connectionInfo.ssid

            wifiManager.disconnect()
            var doingNow = "Disconnecting from AP ..."

            doingBefore = caller.wifiOperationProgress(WifiOperation.DISCONNECT_FROM_AP, OperationState.START, timeout, doingNow) ?: ""

            // Aguarda sinalização da desconexao
            Timber.i("Aguardando desconexao...");

            while ( true ) {
                tempoDecorrido = System.currentTimeMillis() -  startTime
                if ( tempoDecorrido >= timeout ) {
                    break
                }

                progress = (tempoDecorrido * 100 / timeout).toInt()
                if ( progress > 100) progress = 100

                caller.wifiOperationProgress(WifiOperation.DISCONNECT_FROM_AP, OperationState.PROGRESS, progress, doingNow)

                try {
                    sleep(50)
                } catch (e: Exception) {
                    Timber.d("Ocorreu uma Exception em sleep")
                    e.printStackTrace()
                    break
                }

                Timber.e("        ssid=[${wifiManager.connectionInfo.ssid}]  networkId=${wifiManager.connectionInfo.networkId}");

                if ( wifiManager.connectionInfo.ssid == "") {
                    Timber.d("wifiManager.connectionInfo.ssid == \"\"")
                    returnValue = disconnectedNetwork
                    break
                }

                if ( wifiManager.connectionInfo.networkId < 0 ) {
                    Timber.d("wifiManager.connectionInfo.networkId < 0")
                    returnValue = disconnectedNetwork
                    break
                }

            }
        } catch (e: Exception) {
            Timber.d("Ocorreu uma Exception ")
            e.printStackTrace()
        }

        caller.wifiOperationProgress(WifiOperation.DISCONNECT_FROM_AP, OperationState.FINISH, progress, doingBefore)

        Timber.i("<<<< Saindo de disconnectFromWiFiNetwork FIM returnValue=${returnValue}")

        return returnValue
    }


    //connects to the given ssid
    fun reconnectWiFiNetwork(caller: WiFiOperationProgress, ssid:String, tag:String, timeout:Int=10_000) : Boolean {
        val startTime = System.currentTimeMillis()
        var tempoDecorrido = 0L
        var progress = (tempoDecorrido * 100 / timeout).toInt()
        var ret = false

        newNetwork = ""

        Timber.i("entrando em connectToWPAWiFi from ${tag}: ${ssid}");

        var wifiConfig= getWiFiConfig("\"" + ssid + "\"")
        if ( wifiConfig == null ) {
            Timber.i("Não localizou SSID: ${ssid}");
            return false
        }

        Timber.i("WWW 1 newNetwork=$newNetwork");

        if ( ! wifiManager.enableNetwork(wifiConfig.networkId,true) ) {
            Timber.e("falha em enableNetwork SSID : ${ssid}");
            return false
        }

        Timber.i("WWW 2 newNetwork=$newNetwork");

        val doingNow = "Connecting to AP ${ssid}"
        var doingBefore = caller.wifiOperationProgress(WifiOperation.CONNECT_TO_AP, OperationState.START, timeout, doingNow) ?: ""


        Timber.i("chamando reconnect SSID : ${ssid}");
        Timber.i("WWW 3 newNetwork=$newNetwork");

        var retReconnect =  wifiManager.reconnect()

        Timber.i("WWW 4 newNetwork=$newNetwork    retReconnect=$retReconnect");

        if ( retReconnect ) {

            // Aguarda sinalização da nova rede conectada
            Timber.i("Sucesso em reconnect SSID : ${ssid}");

            while ( newNetwork == "" ) {
                tempoDecorrido = System.currentTimeMillis() -  startTime
                if ( tempoDecorrido >= timeout ) {
                    break
                }
                progress = (tempoDecorrido * 100 / timeout).toInt()
                if ( progress > 100) progress = 100

                caller.wifiOperationProgress(WifiOperation.CONNECT_TO_AP, OperationState.PROGRESS, progress, doingNow)
                sleep(50)
            }

            if ( comparaSSID(newNetwork, ssid) ) {
                ret = true
                Timber.i("=====>>>> Sucesso ao conectar em  SSID : ${ssid}");
            } else {
                ret = false
                Timber.e("=====>>>> FALHA ao conectar em  SSID : ${ssid} : Conectou em : wifiManager.connectionInfo.ssid");
            }
        } else {
            Timber.e("falha em reconnect SSID : ${ssid}");
        }

        caller.wifiOperationProgress(WifiOperation.CONNECT_TO_AP, OperationState.FINISH, progress, doingBefore)

        if ( ret ) {
            caller.wifiNotificationEvent(WifiEvent.CONNECT_TO_AP, ssid)
        } else {
            caller.wifiNotificationEvent(WifiEvent.CONNECT_TO_AP_ERROR, ssid)
        }

        return ret
    }


    fun updateWPAProfile(conf:WifiConfiguration, ssid: String, pass: String) : Int {
        conf.SSID = "\"" + ssid + "\""
        conf.preSharedKey = "\"" + pass + "\""
        var ret = wifiManager.updateNetwork(conf)
        return ret
    }

    fun createWPAProfile(caller: WiFiOperationProgress?, ssid: String, pass: String) : Int {
        val conf = WifiConfiguration()
        var newId : Int

        conf.SSID = "\"" + ssid + "\""
        conf.preSharedKey = "\"" + pass + "\""
        newId = wifiManager.addNetwork(conf)

        if ( caller != null ) {
            if ( newId < 0 ) {
                caller.wifiNotificationEvent(WifiEvent.NEW_AP_ERROR, ssid)
            } else {
                caller.wifiNotificationEvent(WifiEvent.NEW_AP_CREATED, ssid)
            }
        }

        return newId
    }



    @Suppress("VARIABLE_WITH_REDUNDANT_INITIALIZER")
    fun comparaSSID(s1:String?, s2:String?) : Boolean {
        var start=0
        var end=0

        if ( s1 == null && s2 == null ) {
            return true
        }

        if ( (s1 == null) || (s2 == null) ) {
            return false
        }

        if ( (s1.length == 0) || (s2.length == 0) ) {
            return false
        }


        // ----- S1
        start=0
        while ( s1[start] == '"' ) start++
        end=s1.lastIndex
        while ( s1[end] == '"' ) end--
        var p1 = s1.subSequence(start, end+1)

        // ----- S2
        start=0
        while ( s2[start] == '"' ) start++
        end=s2.lastIndex
        while ( s2[end] == '"' ) end--
        var p2 = s2.subSequence(start, end+1)

        //Timber.i(String.format("s1=%-20s  s2=%-20s  p1=%-20s p2=%-20s", s1, s2, p1, p2) + " ret=${p1==p2}")

        return p1==p2

//        if ( s1[start] == )
//        if ( s1.contains("\"")) {
//            str1 = s1.drop(1)
//            str1 = str1.dropLast(1)
//        }
//        if ( s2.contains("\"")) {
//            str2 = s2.drop(1)
//            str2 = str2.dropLast(1)
//        }
//        return str1==str2
    }

}



