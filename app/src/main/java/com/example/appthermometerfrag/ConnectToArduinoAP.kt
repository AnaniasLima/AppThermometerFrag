@file:Suppress("DEPRECATION")
package com.example.appthermometerfrag

import android.os.AsyncTask
import android.os.StrictMode
import timber.log.Timber
import java.io.*


class ConnectToArduinoAPAndConfigure(val connectToAPArduino: ConnectToAP, val ssid:String, val passwd: String) : AsyncTask<Int, String?, String>() , WifiController.WiFiOperationProgress {
    private var connectSuccess: Boolean = true
    var responseFromThermometer : String? = null
    val startTime = System.currentTimeMillis()
    var timeout = 0
    var doing:String=""

    interface ConnectToAP {
        fun connectArduinoAPProgress(state:OperationState, progressPerc:Int, mac:String)
    }

    override fun wifiOperationProgress(operation: WifiOperation, state:OperationState, progressPerc:Int, msg:String) : String? {
        var tempoDecorrido = System.currentTimeMillis() -  startTime
        var progressFull = (tempoDecorrido * 100 / timeout).toInt()
        var doingBefore : String? = null

//        var progress = progressPerc
//        if ( progress > 100) progress=100


        Timber.i("wifiOperationProgress (operation=${operation}, state= $state}, progressPerc=${progressPerc}, msg:${msg})")

        if ( progressFull > 100) progressFull=100

        when (state ) {
            OperationState.START -> {
                doingBefore = doing
                doing = msg
//                connectToAPArduino.connectArduinoAPProgress(OperationState.PROGRESS, 0, doing)
            }

            OperationState.PROGRESS -> {
//                connectToAPArduino.connectArduinoAPProgress(OperationState.PROGRESS, progressFull, doing)
            }

            OperationState.FINISH -> {
//                connectToAPArduino.connectArduinoAPProgress(OperationState.PROGRESS, 0, doing)
                doing = msg
                doingBefore = null
            }
        }

        publishProgress(progressFull.toString(), doing)

        return doingBefore
    }

    override fun wifiNotificationEvent(event : WifiEvent, msg:String) {
        Timber.i("WifiEvent=${event}  msg=${msg}")
    }


    override fun onPreExecute() {
        super.onPreExecute()
        Timber.i("Aguardando conexao...")
        ArduinoWifiDevice.macDevice = ""
        connectToAPArduino.connectArduinoAPProgress(OperationState.START, 0, "")
    }

    override fun onProgressUpdate(vararg values: String?) {
        var progress=0
        var msg=""
        super.onProgressUpdate(*values)

        if ( values[0] != null ) {
            progress = values[0].toString().toInt()
            if ( progress > 100) progress=100
        }

        if ( values[1] != null ) {
            msg = values[1].toString()
        }

        Timber.i("ConnectToArduinoAPAndConfigure/onProgressUpdate: progress=$progress   msg=$msg")
        connectToAPArduino.connectArduinoAPProgress(OperationState.PROGRESS, progress, msg)
    }

    override fun doInBackground(vararg params: Int?): String {
        var idRedeArduino : Int = 0
        var msgErro = ""
        var redeAtual: String? = null

        timeout = (params[0] ?: 0 )
        if (timeout <= 0 ) {
            timeout = 10_000
        }

        responseFromThermometer = null

        Timber.i("doInBackground timeout=${timeout}")
        try {
            var fase = 1

            process@while ( true ) {
                var tempoDecorrido = System.currentTimeMillis() -  startTime
                var progress = (tempoDecorrido * 100 / timeout).toInt()

                if ( progress > 100) progress=100

                if ( tempoDecorrido >= timeout ) {
                    break
                }

                publishProgress(progress.toString(), null)
                Timber.i("FASE ${fase} =====================")

                when (fase) {
                    1 -> { // Se necessário, Cria profile para se conectar a rede ArduinoSSID
                        if ( WifiController.getWiFiConfig("\"" + ArduinoWifiDevice.APDefaultSSID + "\"") == null ) {
                            idRedeArduino = WifiController.createWPAProfile(this, ArduinoWifiDevice.APDefaultSSID, ArduinoWifiDevice.APDefaultPasswd)
                            if ( idRedeArduino <= 0 ) {
                                Timber.e("Erro na criação da rede ${idRedeArduino}")
                                msgErro = "Erro na criação do profile da rede " + ArduinoWifiDevice.APDefaultSSID
                                break@process
                            } else {
                                fase = 2
                            }
                        }
                    }

                    2 -> { // Desconecta da rede atual
                        redeAtual = WifiController.disconnectFromWiFiNetwork( this, "REDE_LOCAL")
                        if ( redeAtual == null ) { // não conseguiu desconectar
                            msgErro = "Erro ao tentar desconectar da rede atual"
                            fase = 9
                        } else {
                            fase = 3
                        }
                    }

                    3-> { //
                        if (  WifiController.reconnectWiFiNetwork(this, ArduinoWifiDevice.APDefaultSSID, "REDE_ARDUINO") ) {
                            if (WifiController.isConnectedTo(ArduinoWifiDevice.APDefaultSSID)) {
                                Timber.i("Estamos conectado na rede ${ArduinoWifiDevice.APDefaultSSID}")
                                fase = 4
                                continue@process
                            } else {
                                WifiController.disconnectFromWiFiNetwork(this, ArduinoWifiDevice.APDefaultSSID)
                            }
                        }
                        msgErro = "Falha ao conectar em " + ArduinoWifiDevice.APDefaultSSID
                        Timber.i("Setando msgErro=${msgErro}")
                        fase = 8
                    }

                    4 -> { //
                        responseFromThermometer = configuraThermometer(ssid, passwd )
                        fase = 8
                    }

                    8 -> { // Reconecta na rede Original
                        Timber.i("FASE ${fase} redeAtual ${redeAtual}")
                        if ( redeAtual != null ) {
                            if ( ! WifiController.isConnectedTo(redeAtual)) {
                                WifiController.reconnectWiFiNetwork(this, redeAtual, "REDE_ORIGINAL")
                                if (WifiController.isConnectedTo(redeAtual)) {
                                    Timber.i("Estamos de volta na rede ${redeAtual}")
                                }
                            }
                        }
                        fase = 9
                    }

                    9 -> { // Remove ArduinoSSID da lista de redes cadastradas
                        if ( (idRedeArduino > 0) && WifiController.wifiManager.removeNetwork(idRedeArduino) ) {
                            Timber.i("Removida rede ${ArduinoWifiDevice.APDefaultSSID}")
                        } else {
                            Timber.e("Erro na exclusao da rede ${ArduinoWifiDevice.APDefaultSSID}")
                        }
                        break@process
                    }
                }

                Thread.sleep(50)
//                connectToAPArduino.connectArduinoAPProgress(OperationState.PROGRESS, progress, "")
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        if ( msgErro.isEmpty() ) {
            Timber.i("Encerrando doInBackground")
        } else {
            Timber.e("Encerrando doInBackground com msgErro=[$msgErro}]")
        }

        return msgErro
    }

    override fun onPostExecute(result: String) {
        super.onPostExecute(result)

        if ( (responseFromThermometer?.length ?: 0) == 17 ) {
            // 2e:f4:32:5d:e7:c9
            ArduinoWifiDevice.macDevice = responseFromThermometer ?: ""
        }

        connectToAPArduino.connectArduinoAPProgress(OperationState.FINISH, 100, result)
    }

    fun configuraThermometer(ssid:String, passwd:String): String? {
        val timeout = 3000
        val PARAM_TIMEOUT_AS_CLIENT = 20
        val PARAM_TIMEOUT_AS_ACCESS_POINT = 60
        val PARAM_TIMEOUT_CLIENT_WAITING_SOCKET = 240
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        var response: String? = null
        var commandToSend : String

        if ( ssid == "") {
            commandToSend= "ZERA\r\n"
        } else {
            commandToSend = String.format("CONFIG:[%s\t%s\t%d\t%d\t%d]\r\n", ssid, passwd, PARAM_TIMEOUT_AS_CLIENT, PARAM_TIMEOUT_AS_ACCESS_POINT, PARAM_TIMEOUT_CLIENT_WAITING_SOCKET)
        }

        Timber.i("Entrando em configuraThermometer")

        var socket = WifiController.openSocket(this, ArduinoWifiDevice.APDefaultIP, ArduinoWifiDevice.APDefaultPort)

        if (  socket != null ) {
            response = WifiController.socketTransaction(this, socket, commandToSend, timeout)
            socket.close()
        }

        return response
    }

}

