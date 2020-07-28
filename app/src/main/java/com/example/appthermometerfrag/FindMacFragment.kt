package com.example.appthermometerfrag

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import timber.log.Timber
import java.lang.RuntimeException

private const val ARG_SSID_REDE= "ssid"
private const val ARG_PASSWD_REDE = "passwd"

/**
 * Use the [FindMacFragment.newInstance()] factory method to
 * create an instance of this fragment.
 */
class FindMacFragment : Fragment() , ConnectToArduinoAPAndConfigure.ConnectToAP {
    private var listener : FindMacListener? = null
    private var ssidDaRede: String? = ""
    private var passwdDaRede: String? = ""
    private lateinit var buttonConfig : Button
    private lateinit var buttonReset : Button
    private lateinit var progressBar : ProgressBar
    private lateinit var label_doing : TextView
    private lateinit var edt_ssid : EditText
    private lateinit var edt_passwd : EditText

    interface FindMacListener {
        fun onMacFinded(mac : String?)
        fun onMacFinished()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            ssidDaRede = it.getString(ARG_SSID_REDE)
            passwdDaRede = it.getString(ARG_PASSWD_REDE)
        }
        Timber.i("onCreate de FindMacFragment(${ssidDaRede}, ${passwdDaRede})")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        var fragView =  inflater.inflate(R.layout.fragment_find_mac, container, false)

        Timber.i("onCreateView de FindMacFragment")

        fragView.visibility = View.VISIBLE

        buttonConfig = fragView.findViewById(R.id.btn_configThermometer)
        buttonConfig.isEnabled = false

        buttonReset = fragView.findViewById(R.id.btn_resetThermometer)
        buttonReset.isEnabled = true


        edt_ssid = fragView.findViewById(R.id.et_ssidDaRede)
        edt_passwd = fragView.findViewById(R.id.et_passwd)


        progressBar = fragView.findViewById(R.id.progressBar)
        label_doing = fragView.findViewById(R.id.tv_doing)

        buttonConfig.setOnClickListener {
            buttonConfig.isEnabled = false
            Timber.i("Click no buttonConfig")
            connectToAccessPointAndGetMacAddress()
        }

        if ( view != null) {
            Timber.i("view() NOT null em  FindMacFragment em BBBB")
        }
        return fragView
    }


    override fun onAttach(context: Context) {
        super.onAttach(context)
        Timber.i("onAttach de FindMacFragment")

        if ( context is FindMacListener ) {
            listener = context
        } else {
            throw RuntimeException( context.toString() + " Must implement onMacFinded and onMacFinished")
        }
    }

    override fun onDetach() {
        super.onDetach()
        Timber.i("onDetach de FindMacFragment")
        listener?.onMacFinished()

        listener = null
    }

    companion object {
        const val ARG_TEXT = "Text"
        const val ARG_NUMBER = "Number"
        fun newInstance(ssidDaRede:String, passwdDaRede:String) : FindMacFragment{
            var fragment = FindMacFragment()
            var args = Bundle()

            Timber.i("newInstance de FindMacFragment(${ssidDaRede}, ${passwdDaRede})")

            args.putString(ARG_SSID_REDE, ssidDaRede)
            args.putString(ARG_PASSWD_REDE, passwdDaRede)
            fragment.arguments = args

            return fragment
        }
    }


    override fun connectArduinoAPProgress(state: OperationState, progressPerc: Int, mac: String) {
        var progress = progressPerc

        if ( progress > 100) progress = 100

        Timber.i("connectAPArduinoProgress. Flag=${state}   progress=$progress   mac=$mac")

        when (state) {
            OperationState.START -> {
                progressBar.visibility = View.VISIBLE
                label_doing.text = mac
                Timber.i("connectAPArduinoProgress. Atualizando label_doing   text=$mac")
            }
            OperationState.PROGRESS -> {
                progressBar.progress = progress
                label_doing.text = mac
            }
            OperationState.FINISH -> {
                label_doing.text = mac
                progressBar.progress = 0
                progressBar.visibility = View.INVISIBLE
                if ( ArduinoWifiDevice.macDevice != "") {
                    listener?.onMacFinded(ArduinoWifiDevice.macDevice)
                    listener?.onMacFinished()
                    getActivity()?.onBackPressed()
                } else {
                    buttonConfig.isEnabled = true
                }
            }
        }
    }

    fun connectToAccessPointAndGetMacAddress() {
        ConnectToArduinoAPAndConfigure(this, ssidDaRede!!, passwdDaRede!!).execute(20_000)
    }
}