package org.halalz.kahftube.view

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.duckduckgo.app.browser.databinding.ActivityProfilePageBinding
import com.duckduckgo.app.kahftube.SharedPreferenceManager
import com.duckduckgo.app.kahftube.SharedPreferenceManager.KeyString
import org.halalz.kahftube.extentions.loadFromUrl

class ProfilePageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfilePageBinding
    private lateinit var sharedPref: SharedPreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityProfilePageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPref = SharedPreferenceManager(this@ProfilePageActivity)
        saveTempDummyInfo()
        prepareViews()
        initListeners()
    }

    private fun saveTempDummyInfo() {
        sharedPref.setValue(KeyString.NAME, "Asif Ahmed")
        sharedPref.setValue(KeyString.EMAIL, "asifddlks8@gmail.com")
        sharedPref.setValue(KeyString.IMAGE_SRC, "https://yt3.ggpht.com/yti/AGOGRCqFABQK7A5GvVn7iUHY3AWK8GX182NEWZAXhw=s88-c-k-c0x00ffffff-no-rj")
        sharedPref.setValue(KeyString.PRACTICING_LEVEL, 1)
        sharedPref.setValue(KeyString.GENDER, 2)
        sharedPref.setValue(KeyString.TOKEN, "1637|VTwoGKekKw5uLze11HEwQc1kExtFnkuqts0chlOw1c46b4ff")
    }

    private fun initListeners() {
        binding.layoutPreference.setOnClickListener {
            startActivity(Intent(this@ProfilePageActivity, PreferencePageActivity::class.java))
        }
        binding.layoutUnsubscribe.setOnClickListener {
            //startActivity(Intent(this@ProfilePageActivity,PreferencePageActivity::class.java))
        }
    }

    private fun prepareViews() {
        binding.toolbar.setNavigationIcon(com.duckduckgo.mobile.android.R.drawable.ic_arrow_left_24)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.textName.text = sharedPref.getValue(KeyString.NAME)
        binding.imageProfile.loadFromUrl(sharedPref.getValue(KeyString.IMAGE_SRC))
    }
}
