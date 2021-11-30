/*
 * Copyright (c) 2021 Blue Jeans Network, Inc. All rights reserved.
 */

package com.bluejeans.android.sdksample

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.bjnclientcore.ui.util.extensions.gone
import com.bjnclientcore.ui.util.extensions.visible
import com.bluejeans.android.sdksample.databinding.FragmentInmeetingBinding
import com.bluejeans.android.sdksample.viewpager.ScreenSlidePagerAdapter
import com.bluejeans.bluejeanssdk.meeting.MeetingService
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import io.reactivex.rxjava3.disposables.CompositeDisposable

/**
 * A InMeeting fragment responsible for showing remote video & remote content.
 */
class InMeetingFragment : Fragment() {

    private val meetingService = SampleApplication.blueJeansSDK.meetingService
    private var videoState: MeetingService.VideoState? = null
    private var inMeetingFragmentBinding: FragmentInmeetingBinding? = null
    private var pagerAdapter: ScreenSlidePagerAdapter? = null
    private val disposable = CompositeDisposable()
    private var isRemoteContentAvailable = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        inMeetingFragmentBinding = FragmentInmeetingBinding.inflate(inflater, container, false)
        return inMeetingFragmentBinding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeViews()
        registerForSubscription()
    }

    private fun registerForSubscription() {
        subscribeForRemoteContentState()
        subscribeForVideoState()
    }

    private fun initializeViews() {
        pagerAdapter = ScreenSlidePagerAdapter(this)
        inMeetingFragmentBinding?.vpViewPager?.adapter = pagerAdapter
        inMeetingFragmentBinding?.tabLayout?.let {
            inMeetingFragmentBinding?.vpViewPager?.let { viewPager ->
                TabLayoutMediator(
                    it, viewPager
                ) { _: TabLayout.Tab?, _: Int -> }.attach()
            }
        }
        inMeetingFragmentBinding?.vpViewPager?.registerOnPageChangeCallback(PagerChangeCallback())
        inMeetingFragmentBinding?.vpViewPager?.offscreenPageLimit = 1
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "onConfigurationChanged")
        /* The multi-stream remote video fragment computes size at run-time, when handling config change and using
        viewpager2, we need to make sure video fragment or video fragment's parent is visible on Config change inorder to
        propagate dimensions at runtime.*/
        inMeetingFragmentBinding?.vpViewPager?.currentItem = 0
    }

    private inner class PagerChangeCallback : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            super.onPageSelected(position)
            when (position) {
                0 -> handleVideoState()
                1 -> handleRemoteContentState()
            }
        }
    }

    private fun subscribeForVideoState() {
        disposable.add(meetingService.videoState.subscribeOnUI(
            { state: MeetingService.VideoState ->
                videoState = state
                when (videoState) {
                    is MeetingService.VideoState.Active -> showInMeetingView()
                    else -> handleVideoState()
                }
            }
        ) {
            Log.e(TAG, "Error in video state subscription")
        })
    }

    private fun subscribeForRemoteContentState() {
        disposable.add(meetingService.contentShareService.receivingRemoteContent.subscribeOnUI({ isReceivingRemoteContent ->
            if (isReceivingRemoteContent != null) {
                isRemoteContentAvailable = isReceivingRemoteContent
            }
            when (isReceivingRemoteContent) {
                true -> showInMeetingView()
                else -> handleRemoteContentState()
            }
        }) {
            Log.e(TAG, "Error in remote content subscription")
        })
    }

    private fun handleVideoState() {
        when (videoState) {
            is MeetingService.VideoState.Active -> hideProgress()
            is MeetingService.VideoState.Inactive.SingleParticipant ->
                showProgress("You are the only participant. Please wait some one to join.")
            is MeetingService.VideoState.Inactive.NoOneHasVideo ->
                showProgress("No one is sharing their video")
            is MeetingService.VideoState.Inactive.NeedsModerator ->
                showProgress("Need moderator")
            else -> hideProgress()
        }
    }

    private fun handleRemoteContentState() {
        when {
            isRemoteContentAvailable -> hideProgress()
            else -> showProgress("No one is sharing the remote content.")
        }
    }

    private fun showInMeetingView() {
        inMeetingFragmentBinding?.vpViewPager?.visible()
        inMeetingFragmentBinding?.tvInMeetingState?.gone()
    }

    private fun showProgress(msg: String) {
        inMeetingFragmentBinding?.tvInMeetingState?.visible()
        inMeetingFragmentBinding?.tvInMeetingState?.text = msg
    }

    private fun hideProgress() {
        inMeetingFragmentBinding?.tvInMeetingState?.gone()
        inMeetingFragmentBinding?.tvInMeetingState?.text = ""
    }

    companion object {
        const val TAG = "InMeetingFragment"
    }
}