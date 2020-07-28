package com.example.appthermometerfrag

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber

class MainActivity : AppCompatActivity() , ConfigFragment.FindMacListener {
    lateinit var configFragment : ConfigFragment

    var nomeDaRedeWifi: String = ""
    var passwordDaRedeWifi: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        WifiController.start(this, applicationContext)
        ScreenLog.start(this, applicationContext, log_recycler_view, history_recycler_view)

        btn_startupError.setOnClickListener {
            testCurrentWifiNetwork()
        }

        testCurrentWifiNetwork()
    }

    override fun onMacFinded(mac: String?) {
        if ( mac != null) {
            Timber.i("Localizado MAC : ${mac}")
            tv_mostraMac.text = mac
        }
    }

    override fun onMacFinished() {
        Timber.i("onMacFinished")
        btn_config.visibility = View.VISIBLE
        tv_mostraMac.visibility = View.VISIBLE
        container_a.visibility = View.GONE
    }


    /**
     * A princípio nunca utilizaremos o Arduino como AccessPoint. Sendo assim, vamos garantir que
     * o SSID "8266_THERMOMETER" nõa esteja na lista de redes conhecidos. Caso esteja, vamos tentar
     * excluir a rede. Caso não seja possível (uma aplicação não pode excluir uma rede cadastrada por outros)
     * vamos solicitar ao usuário a exclusao da rede antes de prosseguirmos.
     * Quando o Tablet estiver atuando como AccessPoint, vamos pegar o SSID e a PASSWD para passar
     * para o Arduino caso o mesmo ainda não tenha sido configurado.
     * Estando o Tablet conectado numa rede Wifi, vamos pegar o SSID da rede sendo utilizada para
     * que o Arduino se conecte na mesma rede.
     */
    fun testCurrentWifiNetwork() {
        var errorMessage : String? = null
        val fxName = object{}.javaClass.enclosingMethod?.name
        Timber.e("#### ${fxName}  AAA ####")

        nomeDaRedeWifi = ""
        passwordDaRedeWifi = ""

        if ( WifiController.wifiManager.isWifiEnabled) {
            var wifiConfig= WifiController.getWiFiConfig("\"" + ArduinoWifiDevice.APDefaultSSID + "\"")

            Timber.e("#### ${fxName}  BBB ####")

            container_b.visibility = View.VISIBLE
             btn_startupError.setText("  Avaliando Rede... \n  Aguarde... ")
            btn_startupError.isEnabled = false

            Timber.e("#### ${fxName}  CCC ####")

            if ( wifiConfig != null) {

                // Remove ArduinoSSID da lista de redes cadastradas
                @Suppress("DEPRECATION")
                if ( WifiController.wifiManager.removeNetwork(wifiConfig.networkId) ) {
                    Timber.e("Removendo ${ArduinoWifiDevice.APDefaultSSID}")
                    errorMessage="\n Rede \"${ArduinoWifiDevice.APDefaultSSID}\" precisa \n Ser removida \n (Clique para validar Remoção) \n "
                } else {
                    Timber.e("Erro na exclusao da rede ${ArduinoWifiDevice.APDefaultSSID}")
                    errorMessage = "\n Rede \"${ArduinoWifiDevice.APDefaultSSID}\" não \n pode estar previamente cadastrada \n Exclua a rede Wifi \"${ArduinoWifiDevice.APDefaultSSID}\" \n"
                }

            } else if (WifiController.isWifiAccessPointEnabled()) {
                nomeDaRedeWifi = WifiController.getAccessPointSSID()
                passwordDaRedeWifi = WifiController.getAccessPointPassword()
                if ( nomeDaRedeWifi.contains("unknown")) {
                    errorMessage="\nRede Wifi <unknown>\n Favor tentar novamente \n (Ajuste e clique no botão)\n"
                }
            } else {
                nomeDaRedeWifi = WifiController.getCurrentSSID()
                if (nomeDaRedeWifi.length == 0) {
                    errorMessage="\nSem conexão WIFI ativa.\nFavor conectar na mesma\nrede WIFI na qual o \nTermometro deverá ser conectado\n(Ajuste e clique no botão)\n"
                } else if (nomeDaRedeWifi.contains(ArduinoWifiDevice.APDefaultSSID)) {
                    errorMessage = "\nConexão WIFI ativa deve \nser a mesma rede WIFI na qual o " +
                            "\nTermometro deverá ser conectado\n" + "(Ajuste e clique no botão)\n"
                }
            }
        } else {
            errorMessage=" Favor Habilitar \n   Wifi"
        }

        if (errorMessage != null ) {
            btn_startupError.setText(errorMessage)
            container_b.visibility = View.VISIBLE
            btn_startupError.isEnabled = true
            return
        }

        // Prepara para próxima fase
        btn_startupError.setText("")
        container_b.visibility = View.GONE

        // Se já conhecemos o MAC do Arduino, vamos tentar conectar
        if ( ArduinoWifiDevice.macDevice != "") {
            Timber.e("#### findIP(TIMEOUT_TO_FIND_IP) CCC ####") // TODO:
        } else {
            // Vamos habilitar painel para localizar e configurar o Arduino Access Point
            btn_config.setOnClickListener {
                configFragment = ConfigFragment.newInstance(nomeDaRedeWifi, passwordDaRedeWifi)
                btn_config.visibility = View.GONE
                tv_mostraMac.visibility = View.GONE
                container_a.visibility = View.VISIBLE
                supportFragmentManager.beginTransaction()
                    .replace(R.id.container_a, configFragment)
                    .addToBackStack(null)
                    .commit()
            }
        }

    }

}