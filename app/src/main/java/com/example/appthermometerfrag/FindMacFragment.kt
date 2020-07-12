package com.example.appthermometerfrag

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_find_mac.*
import timber.log.Timber
import java.lang.RuntimeException

private const val ARG_IP = "ip"
private const val ARG_PORT = "port"

/**
 * Use the [FindMacFragment.newInstance()] factory method to
 * create an instance of this fragment.
 */
class FindMacFragment : Fragment() {
    private var listener : FindMacListener? = null
    private var ip: String? = null
    private var port: Int = 0
    private lateinit var buttonOk : Button


    interface FindMacListener {
        fun onMacFinded(mac : String?)
        fun onMacFinished()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            ip = it.getString(ARG_IP)
            port = it.getInt(ARG_PORT)
        }
        Timber.i("onCreate de FindMacFragment(${ip}, ${port})")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        var fragView =  inflater.inflate(R.layout.fragment_find_mac, container, false)


        Timber.i("onCreateView de FindMacFragment")

        fragView.visibility = View.VISIBLE

        buttonOk = fragView.findViewById(R.id.btn_teste1)
        buttonOk.setOnClickListener {
            Timber.i("Click no buttonOk")
            listener?.onMacFinded("11.22.33.44.55")
            getActivity()?.onBackPressed()
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
        val ArduinoSSID = "8266_THERMOMETER"
        val ArduinoPASSWD = "nana12345"
        val ArduinoAccessPointIP = "192.168.4.1"
        val ArduinoAccessPointPort = 81


        const val ARG_TEXT = "Text"
        const val ARG_NUMBER = "Number"
        fun newInstance(ip:String, port:Int) : FindMacFragment{
            var fragment = FindMacFragment()
            var args = Bundle()

            Timber.i("newInstance de FindMacFragment(${ip}, ${port})")

            args.putString(ARG_IP, ip)
            args.putInt(ARG_PORT, port)
            fragment.arguments = args

            return fragment
        }

    }
}