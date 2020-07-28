package com.example.appthermometerfrag

import android.os.AsyncTask
import timber.log.Timber
import java.io.IOException

class BasicAsyncDemo(val basicAsyn: BasicAsyn, val ssid:String, val passwd: String) : AsyncTask<Int, Int, String>() {

    interface BasicAsyn {
        fun basicAsyncProgress(flag:Int, progress:Int, mac:String)
    }


    override fun onPreExecute() {
        super.onPreExecute()
        Timber.i("Aguardando conexao...")
        basicAsyn.basicAsyncProgress(0, 0, "")
    }

    override fun onProgressUpdate(vararg values: Int?) {
        super.onProgressUpdate(*values)
        basicAsyn.basicAsyncProgress(1, 0, "")
    }


    override fun doInBackground(vararg params: Int?): String {
        val startTime = System.currentTimeMillis()
        val timeout = (params[0] ?: 0 )
        Timber.i("doInBackground timeout=${timeout}")
        try {
            var fim = false
            while ( ! fim ) {

                publishProgress()

                var tempoDecorrido = System.currentTimeMillis() -  startTime
                var progress = (tempoDecorrido * 100 / timeout).toInt()

                if ( progress > 100) progress=100

                if ( tempoDecorrido >= timeout ) {
                    break
                }

                Thread.sleep(100)
                basicAsyn.basicAsyncProgress(1, progress, "")
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return ""
    }

    override fun onPostExecute(result: String) {
        super.onPostExecute(result)
        basicAsyn.basicAsyncProgress(2, 100, result)
    }
}
