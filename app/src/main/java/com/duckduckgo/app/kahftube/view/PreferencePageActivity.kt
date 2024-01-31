package org.halalz.kahftube.view

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityPreferencePageBinding
import com.duckduckgo.app.kahftube.SharedPreferenceManager
import com.duckduckgo.app.kahftube.SharedPreferenceManager.KeyString
import org.halalz.kahftube.enums.GenderEnum
import org.halalz.kahftube.enums.PracticingLevelEnum

class PreferencePageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPreferencePageBinding
    private lateinit var sharedPref: SharedPreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreferencePageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPref = SharedPreferenceManager(this@PreferencePageActivity)
        prepareViews()
        initListeners()
    }

    private fun initListeners() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.spinnerGender.setSelection(GenderEnum.entries.find { it.value == sharedPref.getIntValue(KeyString.GENDER) }?.ordinal ?: 0, false)
        binding.spinnerPracticingLevel.setSelection(
            PracticingLevelEnum.entries.find { it.value == sharedPref.getIntValue(KeyString.PRACTICING_LEVEL) }?.ordinal ?: 0, false,
        )

        binding.spinnerGender.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View,
                position: Int,
                id: Long
            ) {
                checkForValueChange()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
            }
        }

        binding.spinnerPracticingLevel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View,
                position: Int,
                id: Long
            ) {
                checkForValueChange()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
            }
        }

        binding.buttonSave.setOnClickListener {
            sharedPref.setValue(KeyString.GENDER, GenderEnum.entries[binding.spinnerGender.selectedItemPosition].value)
            sharedPref.setValue(KeyString.PRACTICING_LEVEL, PracticingLevelEnum.entries[binding.spinnerPracticingLevel.selectedItemPosition].value)

            binding.buttonSave.visibility = View.GONE
        }
    }

    private fun checkForValueChange() {
        if (
            GenderEnum.entries[binding.spinnerGender.selectedItemPosition].ordinal != GenderEnum.entries.find {
                it.value == sharedPref.getIntValue(
                    KeyString.GENDER,
                )
            }?.ordinal
            ||
            PracticingLevelEnum.entries[binding.spinnerPracticingLevel.selectedItemPosition].ordinal != PracticingLevelEnum.entries.find {
                it.value == sharedPref.getIntValue(
                    KeyString.PRACTICING_LEVEL,
                )
            }?.ordinal
        ) {
            binding.buttonSave.visibility = View.VISIBLE
        } else {
            binding.buttonSave.visibility = View.GONE
        }
    }

    private fun prepareViews() {
        binding.toolbar.setNavigationIcon(com.duckduckgo.mobile.android.R.drawable.ic_arrow_left_24)

        val genderSpinnerAdapter = ArrayAdapter(this, R.layout.item_spinner, GenderEnum.entries.map { it.display })
        binding.spinnerGender.adapter = genderSpinnerAdapter

        val practicingLevelSpinnerAdapter = ArrayAdapter(this, R.layout.item_spinner, PracticingLevelEnum.entries.map { it.display })
        binding.spinnerPracticingLevel.adapter = practicingLevelSpinnerAdapter
    }
}
