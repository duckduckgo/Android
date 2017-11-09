package com.duckduckgo.app.main

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.duckduckgo.app.trackerdetection.TrackerDetector
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onResume() {
        super.onResume()

        Log.d("TRACKERS", "Reading tracker files")
        val easylistData = resources.openRawResource(R.raw.easylist).use { it.readBytes() }
        val easyprivacyData = resources.openRawResource(R.raw.easyprivacy).use { it.readBytes() }

        Log.d("TRACKERS", "Parsing")

        val trackerDetector = TrackerDetector(easylistData, easyprivacyData)

        Log.d("TRACKERS", "Matching trackers")

        var documentUrl = "example.com"
        val blockedEasy = trackerDetector.shouldBlock("http://imasdk.googleapis.com/js/sdkloader/ima3.js", documentUrl)
        val blockedEasyPrivacy = trackerDetector.shouldBlock("http://cdn.tagcommander.com/1705/tc_catalog.js", documentUrl)
        val blockedNone = trackerDetector.shouldBlock("https://duckduckgo.com/index.html", documentUrl)

        Log.d("TRACKERS", "Done!")

        Log.d("BLOCKED", "Easy tracker: " + blockedEasy)
        Log.d("BLOCKED", "EasyPrivacy tracker: " + blockedEasyPrivacy)
        Log.d("BLOCKED", "Nontracker: " + blockedNone)

        welcomeText.text = "DONE!"
    }

}
