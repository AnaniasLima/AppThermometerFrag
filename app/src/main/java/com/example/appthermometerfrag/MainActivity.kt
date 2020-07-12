package com.example.appthermometerfrag

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber

class MainActivity : AppCompatActivity() , FindMacFragment.FindMacListener {
    lateinit var findMacFragment : FindMacFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        findMacFragment = FindMacFragment.newInstance("192.168.4.1", 80)

        btn_findMac.setOnClickListener {
            btn_findMac.visibility = View.GONE
            tv_mostraMac.visibility = View.GONE
            container_a.visibility = View.VISIBLE
            supportFragmentManager.beginTransaction()
                .replace(R.id.container_a, findMacFragment)
                .addToBackStack(null)
                .commit()

        }

    }

    override fun onMacFinded(mac: String?) {
        if ( mac != null) {
            Timber.i("Localizado MAC : ${mac}")
            tv_mostraMac.text = mac
        }
    }

    override fun onMacFinished() {
        Timber.i("onMacFinished")
        btn_findMac.visibility = View.VISIBLE
        tv_mostraMac.visibility = View.VISIBLE
        container_a.visibility = View.GONE
    }

}