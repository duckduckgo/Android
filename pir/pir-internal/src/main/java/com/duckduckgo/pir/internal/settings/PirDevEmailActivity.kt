/*
 * Copyright (c) 2026 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.pir.internal.settings

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.pir.impl.email.PirEmailConfirmationJobsRunner
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.impl.store.PirSchedulingRepository
import com.duckduckgo.pir.internal.databinding.ActivityPirInternalEmailBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(PirDevEmailScreenNoParams::class)
class PirDevEmailActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var pirSchedulingRepository: PirSchedulingRepository

    @Inject
    lateinit var pirRepository: PirRepository

    @Inject
    lateinit var pirEmailConfirmationJobsRunner: PirEmailConfirmationJobsRunner

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    private val binding: ActivityPirInternalEmailBinding by viewBinding()

    private lateinit var spinnerAdapter: ArrayAdapter<String>
    private val emailJobs = mutableListOf<EmailJobDisplayItem>()
    private var selectedJob: EmailJobDisplayItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.toolbar)
        setupViews()
        loadEmailJobs()
    }

    private fun setupViews() {
        spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, mutableListOf<String>())
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.emailJobsSpinner.adapter = spinnerAdapter

        binding.emailJobsSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long,
            ) {
                if (position < emailJobs.size) {
                    selectedJob = emailJobs[position]
                    updateJobDetails()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedJob = null
                updateJobDetails()
            }
        }

        binding.fetchLink.setOnClickListener {
            fetchLink()
        }

        binding.runEmailConfirmation.setOnClickListener {
            runEmailConfirmation()
        }

        binding.viewEmailResults.setOnClickListener {
            globalActivityStarter.start(this, PirResultsScreenParams.PirEmailResultsScreen)
        }
    }

    private fun loadEmailJobs() {
        lifecycleScope.launch {
            val jobsWithNoLink = withContext(dispatcherProvider.io()) {
                pirSchedulingRepository.getEmailConfirmationJobsWithNoLink()
            }
            val jobsWithLink = withContext(dispatcherProvider.io()) {
                pirSchedulingRepository.getEmailConfirmationJobsWithLink()
            }

            val allJobs = jobsWithNoLink + jobsWithLink
            if (allJobs.isEmpty()) {
                binding.noJobsMessage.isVisible = true
                binding.emailJobsSpinner.isVisible = false
                binding.jobDetailsText.text = getString(com.duckduckgo.pir.internal.R.string.pirDevEmailNoJobSelected)
                return@launch
            }

            val profileIds = allJobs.map { it.userProfileId }.distinct()
            val profiles = withContext(dispatcherProvider.io()) {
                pirRepository.getUserProfileQueriesWithIds(profileIds)
            }.associateBy { it.id }

            emailJobs.clear()
            allJobs.forEach { job ->
                val profile = profiles[job.userProfileId]
                emailJobs.add(
                    EmailJobDisplayItem(
                        record = job,
                        profileName = profile?.fullName ?: "Unknown",
                        profileId = job.userProfileId,
                        brokerName = job.brokerName,
                        extractedProfileId = job.extractedProfileId,
                        hasLink = job.linkFetchData.emailConfirmationLink.isNotEmpty(),
                    ),
                )
            }

            val displayStrings = emailJobs.map { item ->
                val linkStatus = if (item.hasLink) "Has Link" else "No Link"
                "${item.profileName} (ID: ${item.profileId}) - ${item.brokerName} [$linkStatus]"
            }

            spinnerAdapter.clear()
            spinnerAdapter.addAll(displayStrings)
            binding.noJobsMessage.isVisible = false
            binding.emailJobsSpinner.isVisible = true

            if (emailJobs.isNotEmpty()) {
                selectedJob = emailJobs[0]
                updateJobDetails()
            }
        }
    }

    private fun updateJobDetails() {
        val job = selectedJob
        if (job == null) {
            binding.jobDetailsText.text = getString(com.duckduckgo.pir.internal.R.string.pirDevEmailNoJobSelected)
            return
        }

        val linkStatus = if (job.hasLink) "Yes" else "No"
        val details = buildString {
            appendLine("Profile Name: ${job.profileName}")
            appendLine("Profile ID: ${job.profileId}")
            appendLine("Broker: ${job.brokerName}")
            appendLine("Extracted Profile ID: ${job.extractedProfileId}")
            appendLine("Email: ${job.record.emailData.email}")
            appendLine("Has Confirmation Link: $linkStatus")
            if (job.hasLink) {
                appendLine("Link: ${job.record.linkFetchData.emailConfirmationLink}")
            }
        }
        binding.jobDetailsText.text = details
    }

    private fun fetchLink() {
        val job = selectedJob
        if (job == null) {
            Toast.makeText(this, "No job selected", Toast.LENGTH_SHORT).show()
            return
        }

        if (job.hasLink) {
            Toast.makeText(this, "Job already has a link", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            Toast.makeText(this@PirDevEmailActivity, "Fetching confirmation link...", Toast.LENGTH_SHORT).show()
            val result = pirEmailConfirmationJobsRunner.debugFetchLinkForJob(job.record)
            if (result.isSuccess) {
                Toast.makeText(this@PirDevEmailActivity, "Link fetched successfully!", Toast.LENGTH_SHORT).show()
                // Reload the jobs to show updated state
                loadEmailJobs()
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                Toast.makeText(this@PirDevEmailActivity, "Failed to fetch link: $error", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun runEmailConfirmation() {
        val job = selectedJob
        if (job == null) {
            Toast.makeText(this, "No job selected", Toast.LENGTH_SHORT).show()
            return
        }

        if (!job.hasLink) {
            Toast.makeText(this, "Job has no confirmation link. Fetch link first.", Toast.LENGTH_SHORT).show()
            return
        }

        // Navigate to WebView activity for debug execution
        globalActivityStarter.start(this, PirDevWebViewScreenParams.PirDevEmailWebViewScreenParams(job.extractedProfileId))
    }

    private data class EmailJobDisplayItem(
        val record: EmailConfirmationJobRecord,
        val profileName: String,
        val profileId: Long,
        val brokerName: String,
        val extractedProfileId: Long,
        val hasLink: Boolean,
    )
}

object PirDevEmailScreenNoParams : ActivityParams
