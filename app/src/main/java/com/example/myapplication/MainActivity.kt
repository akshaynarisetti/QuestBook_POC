package com.example.myapplication

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.Manifest

import com.acrcloud.rec.*
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.acrcloud.rec.utils.ACRCloudLogger
import com.acrcloud.rec.ACRCloudClient
import com.acrcloud.rec.ACRCloudConfig
import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import org.json.JSONObject
import org.json.JSONArray


class MainActivity : AppCompatActivity(),IACRCloudListener {

    private val TAG = "MainActivity"

    private var mProcessing = false
    private var mAutoRecognizing = false
    private var initState = false

    private var path = ""

    private var startTime: Long = 0
    private val stopTime: Long = 0

    private val PRINT_MSG = 1001

    private var mConfig: ACRCloudConfig = ACRCloudConfig()
    private var mClient: ACRCloudClient = ACRCloudClient()

    private var mVolume: TextView? = null
    private var mResult: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mVolume = findViewById<TextView>(R.id.volume)
        mResult = findViewById<TextView>(R.id.result)

        verifyPermissions()

        findViewById<Button>(R.id.start).setOnClickListener(object : View.OnClickListener {

            override fun onClick(arg0: View) {
                start()
            }
        })

        findViewById<Button>(R.id.cancel).setOnClickListener(
            object : View.OnClickListener {

                override fun onClick(v: View) {
                    cancel()
                }
            })


        this.mConfig.acrcloudListener = this
        this.mConfig.context = this
        // Please create project in "http://console.acrcloud.cn/service/avr".
        this.mConfig.host = "identify-ap-southeast-1.acrcloud.com"
        this.mConfig.accessKey = "1c9137040da217d9895cd219dd5ccb8e"
        this.mConfig.accessSecret = "97qhMjGkRtrC7WFQPhi8HnviHS8DSietL6Urli55"

        // If you do not need volume callback, you set it false.
        this.mConfig.recorderConfig.isVolumeCallback = true

        this.mClient = ACRCloudClient()
        this.initState = this.mClient.initWithConfig(this.mConfig)

//        ACRCloudLogger.setLog(true)
    }

    private fun getPredefinedData(): JSONObject {
        val jsonString = """
        {
            "0d159e6975f1bf3feb09a9f33ffb9e35": {
                "url": "https://www.youtube.com/watch?v=w3dn5sbvypY",
                "title": "Baby Animal Dance | CoComelon Nursery Rhymes & Kids Songs"
            },
            "59c1b50c849ebd8e5b2ad115c75bc56b": {
                "url": "https://www.youtube.com/watch?v=Lp4X1tcfjKg",
                "title": "Questbook Intro by Abhilash at EthIndia"
            }
        }
    """.trimIndent()
        return JSONObject(jsonString)
    }

    fun start() {
        if (!this.initState) {
            Toast.makeText(this, "init error", Toast.LENGTH_SHORT).show()
            return
        }

        if (!mProcessing) {
            mProcessing = true
            mVolume?.text = ""
            mResult?.text = "I am Listening......"
            if (this.mClient == null || !this.mClient.startRecognize()) {
                mProcessing = false
                mResult?.text = "start error!"
            }
            startTime = System.currentTimeMillis()
        }
    }

    fun cancel() {
        if (mProcessing && this.mClient != null) {
            this.mClient.cancel()
        }

        this.reset()
    }

    fun reset() {
        mVolume?.text = ""
        mResult?.text = ""
        mProcessing = false
    }

    override fun onResult(results: ACRCloudResult?) {
        this.reset()

        val result = results?.getResult()

        // Parse the JSON response
        val jsonObject = JSONObject(result)

        // Extract the "code" value from the JSON object
        val code = jsonObject.getJSONObject("status").getInt("code")

        if (code == 0) { // Code 0 means the recognition is successful

            val predefinedData = getPredefinedData()
            var acrid = ""
            // Check if the response has custom_files data
            val metadata = jsonObject.getJSONObject("metadata")
            if (metadata.has("custom_files")) {
                // Extract the custom_files array
                val customFilesArray = metadata.getJSONArray("custom_files")

                // Extract the first object from custom_files array
                if (customFilesArray.length() > 0) {
                    val customFile = customFilesArray.getJSONObject(0)
                    acrid = customFile.getString("acrid")
                }
            }

            // Use the ACRID to find the corresponding URL and title
            if (predefinedData.has(acrid)) {
                val videoData = predefinedData.getJSONObject(acrid)
                val title = videoData.getString("title")
                val url = videoData.getString("url")

                // Display the title and URL (use HTML to make the URL clickable)
                val htmlText = "$title<br><a href=\"$url\">$url</a>"
                mResult?.text = Html.fromHtml(htmlText, Html.FROM_HTML_MODE_COMPACT)
                mResult?.movementMethod = LinkMovementMethod.getInstance()
            } else {
                mResult?.text = "The Video could not be found!"
            }
        } else {
            mResult?.text = "The Video could not be found!"
        }
        startTime = System.currentTimeMillis()
    }

    override fun onVolumeChanged(curVolume: Double) {
        val time = (System.currentTimeMillis() - startTime) / 1000
        mVolume?.text = "Volume" + ":" + curVolume.toString() + "\n\nRecord：" + time + " s"
    }

    private val REQUEST_EXTERNAL_STORAGE = 1
    private val PERMISSIONS = arrayOf<String>(
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.INTERNET,
        Manifest.permission.RECORD_AUDIO
    )

    fun verifyPermissions() {
        for (i in PERMISSIONS.indices) {
            val permission = ActivityCompat.checkSelfPermission(this, PERMISSIONS[i])
            if (permission != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this, PERMISSIONS,
                    REQUEST_EXTERNAL_STORAGE
                )
                break
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        Log.e("MainActivity", "release")
        if (this.mClient != null) {
            this.mClient.release()
            this.initState = false
        }
    }
}