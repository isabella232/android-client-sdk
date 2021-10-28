/*
 * Copyright (c) 2021 Blue Jeans Network, Inc. All rights reserved.
 */
package com.bluejeans.android.sdksample

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckedTextView
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.viewpager2.widget.ViewPager2
import com.bjnclientcore.inmeeting.contentshare.ContentShareType
import com.bjnclientcore.ui.util.extensions.gone
import com.bjnclientcore.ui.util.extensions.visible
import com.bluejeans.android.sdksample.MeetingNotificationUtility.updateNotificationMessage
import com.bluejeans.android.sdksample.databinding.ActivityMainBinding
import com.bluejeans.android.sdksample.dialog.WaitingRoomDialog
import com.bluejeans.android.sdksample.menu.MenuFragment
import com.bluejeans.android.sdksample.menu.adapter.MenuItemAdapter
import com.bluejeans.android.sdksample.participantlist.ParticipantListFragment
import com.bluejeans.android.sdksample.utils.AudioDeviceHelper.Companion.getAudioDeviceName
import com.bluejeans.android.sdksample.viewpager.ScreenSlidePagerAdapter
import com.bluejeans.bluejeanssdk.devices.AudioDevice
import com.bluejeans.bluejeanssdk.devices.VideoDevice
import com.bluejeans.bluejeanssdk.logging.LoggingService
import com.bluejeans.bluejeanssdk.meeting.*
import com.bluejeans.bluejeanssdk.permission.PermissionService
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber

class MainActivity : AppCompatActivity(), View.OnClickListener {

    private val permissionService = SampleApplication.blueJeansSDK.permissionService
    private val meetingService = SampleApplication.blueJeansSDK.meetingService
    private val videoDeviceService = SampleApplication.blueJeansSDK.videoDeviceService
    private val loggingService = SampleApplication.blueJeansSDK.loggingService
    private val appVersionString = "v" + SampleApplication.blueJeansSDK.version
    private val disposable = CompositeDisposable()
    private val joinMeetingDisposable = CompositeDisposable()

    private var videoState: MeetingService.VideoState? = null

    private var bottomSheetFragment: MenuFragment? = null
    private var participantListFragment: ParticipantListFragment? = null
    private var videoDeviceAdapter: MenuItemAdapter<VideoDevice>? = null
    private var audioDeviceAdapter: MenuItemAdapter<AudioDevice>? = null
    private var videoLayoutAdapter: MenuItemAdapter<String>? = null
    private var currentVideoLayout: MeetingService.VideoLayout = MeetingService.VideoLayout.Speaker
    private var audioDeviceDialog: AlertDialog? = null
    private var videoDeviceDialog: AlertDialog? = null
    private var videoLayoutDialog: AlertDialog? = null
    private var cameraSettingsDialog: AlertDialog? = null
    private var uploadLogsDialog: AlertDialog? = null
    private var progressBar: ProgressBar? = null

    private lateinit var binding: ActivityMainBinding
    private var zoomScaleFactor = 1 // default value of 1, no zoom to start with

    private var isAudioMuted = false
    private var isVideoMuted = false
    private var isRemoteContentAvailable = false
    private var isInWaitingRoom = false
    private var isWaitingRoomEnabled = false
    private var isScreenShareInProgress = false
    private var isSubscribeToWaitingRoomEvents = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        permissionService.register(this)
        initViews()
        checkCameraPermissionAndStartSelfVideo()
        activateSDKSubscriptions()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        when (newConfig.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> configurePortraitView()
            else -> configureLandscapeView()
        }
        binding.viewPager.currentItem = 0
    }

    override fun onResume() {
        super.onResume()

        if (!isScreenShareInProgress) {
            handleMeetingState(meetingService.meetingState.value)
        }
    }

    override fun onDestroy() {
        Timber.tag(TAG).i("onDestroy")
        disposable.dispose()
        joinMeetingDisposable.clear()
        bottomSheetFragment = null
        isSubscribeToWaitingRoomEvents = false
        super.onDestroy()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.btn_join -> checkMinimumPermissionsAndJoin()
            R.id.imgClose -> {
                meetingService.endMeeting()
                videoDeviceService.enableSelfVideoPreview(!isVideoMuted)
                endMeeting()
            }
            R.id.ivMic -> {
                isAudioMuted = !isAudioMuted
                meetingService.setAudioMuted(isAudioMuted)
                toggleAudioMuteUnMuteView(isAudioMuted)
            }
            R.id.ivVideo -> {
                isVideoMuted = !isVideoMuted
                if (areInMeetingServicesAvailable()) {
                    meetingService.setVideoMuted(isVideoMuted)
                } else {
                    videoDeviceService.enableSelfVideoPreview(!isVideoMuted)
                }
                toggleVideoMuteUnMuteView(isVideoMuted)
            }
            R.id.imgMenuOption -> {
                bottomSheetFragment?.let { it.show(supportFragmentManager, it.tag) }
            }
            R.id.imgRoster -> {
                participantListFragment?.let {
                    supportFragmentManager
                        .beginTransaction()
                        .replace(R.id.rosterContainer, it)
                        .addToBackStack("ParticipantListFragment")
                        .commit()
                }
            }
            R.id.imgScreenShare -> {
                if (meetingService.contentShareService.contentShareState.value is ContentShareState.Stopped) {
                    isScreenShareInProgress = true
                    val mediaProjectionManager =
                        getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    startActivityLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                } else {
                    isScreenShareInProgress = false
                    meetingService.contentShareService.stopContentShare()
                }
            }
            R.id.ivCameraSettings -> showCameraSettingsDialog()
            R.id.imgUploadLogs -> showUploadLogsDialog()
            R.id.btn_exit_waiting_room -> {
                meetingService.endMeeting()
                endMeeting()
            }
        }
    }

    private fun showUploadLogsDialog() {
        uploadLogsDialog = AlertDialog.Builder(this)
            .setView(R.layout.submit_log_dialog).create()
        uploadLogsDialog?.setCanceledOnTouchOutside(true)
        uploadLogsDialog?.show()
        val editText = uploadLogsDialog?.findViewById<EditText>(R.id.description)
        val submitButton = uploadLogsDialog?.findViewById<Button>(R.id.btn_submit)
        progressBar = uploadLogsDialog?.findViewById(R.id.progressBar)
        editText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                submitButton?.isEnabled = s.toString().trim().isNotEmpty()
            }

            override fun afterTextChanged(s: Editable?) {

            }
        })
        submitButton?.setOnClickListener {
            val description = editText?.text.toString()
            submitButton.isEnabled = false
            progressBar?.visible()
            uploadLogs(description)
        }
    }

    private fun uploadLogs(comments: String?) {
        if (!comments.isNullOrEmpty()) {
            disposable.add(
                loggingService.uploadLog(comments, userName)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        { result ->
                            Log.i(TAG, "Log uploaded successfully $result")
                            if (result !== LoggingService.LogUploadResult.Success) {
                                showToastMessage(getString(R.string.upload_logs_failure))
                            } else {
                                showToastMessage(getString(R.string.upload_logs_success))
                            }
                            uploadLogsDialog?.dismiss()
                        },
                        {
                            Log.e(
                                TAG,
                                "Error while uploading logs ${it.message}"
                            )
                            progressBar?.gone()
                            Toast.makeText(
                                this, getString(R.string.upload_logs_failure),
                                Toast.LENGTH_SHORT
                            ).show()
                        })
            )
        } else {
            showToastMessage("Please enter your comments.")
        }
    }

    private fun showToastMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private val startActivityLauncher = registerForActivityResult(
        ActivityResultContracts
            .StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { ContentShareType.Screen(it) }?.let {
                meetingService.contentShareService.startContentShare(it)
            }
        }
    }

    private fun activateSDKSubscriptions() {
        subscribeForMeetingStatus()
        subscribeToWaitingRoomEvents()
        subscribeForVideoMuteStatus()
        subscribeForAudioMuteStatus()
        subscribeForVideoState()
        subscribeForRemoteContentState()
        subscribeForVideoLayout()
        subscribeForAudioDevices()
        subscribeForCurrentAudioDevice()
        subscribeForVideoDevices()
        subscribeForCurrentVideoDevice()
        subscribeForParticipants()
        subscribeForContentShareState()
        subscribeForContentShareAvailability()
        subscribeForContentShareEvents()
        subscribeForClosedCaptionText()
        subscribeForClosedCaptionState()
    }

    private fun checkCameraPermissionAndStartSelfVideo() {
        when {
            permissionService.hasPermission(PermissionService.Permission.Camera) -> startSelfVideo()
            else -> {
                disposable.add(permissionService.requestPermissions(arrayOf(PermissionService.Permission.Camera))
                    .subscribe(
                        { status ->
                            when (status) {
                                PermissionService.RequestStatus.Granted -> startSelfVideo()
                                PermissionService.RequestStatus.NotGranted -> onMultiplePermissionDenied()
                                PermissionService.RequestStatus.NotRegistered -> {
                                    Timber.tag(TAG).i("Permission Service not registered.")
                                    Toast.makeText(
                                        this,
                                        "Permission Service not registered.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    ) {
                        Timber.tag(TAG).e("Error in requesting permission subscription")
                    })
            }
        }
    }

    private fun startSelfVideo() {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.selfViewFrame, videoDeviceService.selfVideoFragment)
            .commit()
        videoDeviceService.enableSelfVideoPreview(true)
    }

    private fun checkMinimumPermissionsAndJoin() {
        if (permissionService.hasMinimumPermissions()) {
            hideKeyboard()
            joinMeeting()
        } else {
            requestMinimumPermissionsAndJoin()
        }
    }

    private fun requestMinimumPermissionsAndJoin() {
        disposable.add(permissionService
            .requestPermissions(arrayOf(PermissionService.Permission.RecordAudio))
            .subscribe(
                { status ->
                    when (status) {
                        PermissionService.RequestStatus.Granted -> joinMeeting()
                        PermissionService.RequestStatus.NotGranted -> onMultiplePermissionDenied()
                        PermissionService.RequestStatus.NotRegistered -> {
                            Timber.tag(TAG).i("Permission Service not registered.")
                            Toast.makeText(
                                this,
                                "Permission Service not registered.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            ) { Timber.tag(TAG).e("Error in requesting permissions subscription") })
    }

    private fun onMultiplePermissionDenied() {
        val deniedPermissions = StringBuilder()
        if (permissionService.listOfDeniedPermission.size > 0) {
            permissionService.listOfDeniedPermission.forEach { permission ->
                deniedPermissions.append("$permission , ")
            }
            val deniedPermissionsString =
                deniedPermissions.substring(0, deniedPermissions.lastIndexOf(","))
            Timber.tag(TAG).i("User Didn't grant $deniedPermissionsString even after asking.")
            Toast.makeText(
                this,
                "User Didn't grant $deniedPermissionsString even after asking.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun joinMeeting() {
        val meetingId = binding.joinInfo.etEventId.text.toString()
        val passcode = binding.joinInfo.etPasscode.text.toString()
        val name = when {
            TextUtils.isEmpty(binding.joinInfo.etName.text.toString()) -> "AndroidSDK"
            else -> binding.joinInfo.etName.text.toString()
        }
        Timber.tag(TAG).i("joinMeeting meetingId = $meetingId passcode = $passcode")
        showJoiningInProgressView()
        joinMeetingDisposable.clear()
        joinMeetingDisposable.add(
            meetingService.joinMeeting(
                MeetingService.JoinParams(meetingId, passcode, name)
            ).subscribeOn(Schedulers.single())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { result ->
                        when (result) {
                            MeetingService.JoinResult.Success -> {
                            }
                            else -> Timber.tag(TAG).i("Join result: $result")
                        }
                    },
                    { showOutOfMeetingView() })
        )
    }

    private fun endMeeting() {
        Timber.tag(TAG).i("endMeeting")
        if (isInWaitingRoom) {
            isInWaitingRoom = false
            showWaitingRoomUI()
        } else {
            showOutOfMeetingView()
        }

        cameraSettingsDialog?.dismiss()
        joinMeetingDisposable.clear()
        OnGoingMeetingService.stopService(this)
    }

    private fun subscribeForMeetingStatus() {
        disposable.add(
            meetingService.meetingState.rxObservable.observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    Log.d(TAG, "State: $it")
                    handleMeetingState(it)
                }, {
                    Log.e(TAG, "${it.message}")
                })
        )
    }

    private fun subscribeToWaitingRoomEvents() {
        disposable.add(meetingService.waitingRoomEvent.observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                when (it) {
                    MeetingService.WaitingRoomEvent.Admitted -> showToastMessage("Moderator has approved")
                    MeetingService.WaitingRoomEvent.Denied -> showToastMessage("Denied by moderator")
                    MeetingService.WaitingRoomEvent.Demoted -> {
                        showToastMessage("Demoted by moderator")
                        videoDeviceService.enableSelfVideoPreview(!isVideoMuted)
                    }
                    else -> Timber.tag(TAG).i("Unrecognized event: $it")
                }
            }, {
                Timber.tag(TAG).e(it.message)
            }))
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
            Timber.tag(TAG).e("Error in video state subscription")
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
            Timber.tag(TAG).e("Error in remote content subscription")
        })
    }

    private fun subscribeForAudioMuteStatus() {
        disposable.add(meetingService.audioMuted.subscribe(
            { isMuted ->
                // This could be due to local mute or remote mute
                Timber.tag(TAG).i(" Audio Mute state $isMuted")
                isMuted?.let { toggleAudioMuteUnMuteView(it) }
            }
        ) {
            Timber.tag(TAG).e("Error in audio mute subscription")
        })
    }

    private fun subscribeForVideoMuteStatus() {
        disposable.add(meetingService.videoMuted.subscribeOnUI(
            { isMuted ->
                // This could be due to local mute or remote mute
                Timber.tag(TAG).i(" Video Mute state $isMuted")
                isMuted?.let { toggleVideoMuteUnMuteView(it) }
            }
        ) {
            Timber.tag(TAG).e("Error in video mute subscription")
        })
    }

    private fun subscribeForVideoLayout() {
        disposable.add(meetingService.videoLayout.subscribe(
            { videoLayout ->
                if (videoLayout != null) {
                    currentVideoLayout = videoLayout
                    val videoLayoutName = getVideoLayoutDisplayName(videoLayout)
                    bottomSheetFragment?.updateVideoLayout(videoLayoutName)
                    updateCurrentVideoLayoutForAlertDialog(videoLayoutName)
                }
            }
        ) {
            Timber.tag(TAG).e("Error in video layout subscription $it")
        })
    }

    private fun subscribeForCurrentAudioDevice() {
        disposable.add(meetingService.audioDeviceService.currentAudioDevice.subscribeOnUI(
            { currentAudioDevice ->
                if (currentAudioDevice != null) {
                    getAudioDeviceName(currentAudioDevice).let {
                        bottomSheetFragment?.updateAudioDevice(it)
                    }
                    updateCurrentAudioDeviceForAlertDialog(currentAudioDevice)
                }
            }
        ) {
            Timber.tag(TAG).e("Error in current audio device subscription $it")
        })
    }

    private fun subscribeForAudioDevices() {
        disposable.add(
            meetingService.audioDeviceService.audioDevices.subscribeOnUI({ audioDevices ->
                if (audioDevices != null) {
                    audioDeviceAdapter?.let {
                        it.clear()
                        it.addAll(audioDevices)
                        it.notifyDataSetChanged()
                    }
                }
            }) {
                Timber.tag(TAG).e("Error in audio devices subscription $it")
            })
    }

    private fun subscribeForCurrentVideoDevice() {
        disposable.add(videoDeviceService.currentVideoDevice.subscribeOnUI(
            { currentVideoDevice ->
                if (currentVideoDevice != null) {
                    bottomSheetFragment?.updateVideoDevice(currentVideoDevice.name)
                    updateCurrentVideoDeviceForAlertDialog(currentVideoDevice)
                }
            }
        ) { err ->
            Timber.tag(TAG).e("Error in current video device subscription $err")
        })
    }

    private fun subscribeForParticipants() {
        disposable.add(meetingService.participantsService.participants.subscribeOnUI(
            {
                participantListFragment?.updateMeetingList(it)
            }
        ) { err ->
            Timber.tag(TAG).e("Error in Participants subscription${err.message}")
        })
    }

    private fun subscribeForContentShareAvailability() {
        disposable.add(meetingService.contentShareService.contentShareAvailability.subscribeOnUI(
            { contentShareAvailability: ContentShareAvailability? ->
                if (contentShareAvailability is ContentShareAvailability.Available) {
                    binding.imgScreenShare.visible()
                } else {
                    binding.imgScreenShare.gone()
                }
            }
        ) { err: Throwable ->
            Log.e(TAG, "Error in content share availability" + err.message)
            Unit
        })
    }

    private fun subscribeForContentShareState() {
        disposable.add(meetingService.contentShareService.contentShareState.subscribeOnUI(
            { contentShareState: ContentShareState? ->
                if (contentShareState is ContentShareState.Stopped) {
                    binding.imgScreenShare.isSelected = false
                    updateNotificationMessage(
                        this,
                        getString(R.string.meeting_notification_message)
                    )
                } else {
                    binding.imgScreenShare.isSelected = true
                    updateNotificationMessage(
                        this,
                        getString(R.string.screen_share_notification_message)
                    )
                }
            }
        ) { err: Throwable ->
            Log.e(
                TAG,
                "Error in content share state subscription $err.message"
            )
        })
    }

    private fun subscribeForContentShareEvents() {
        disposable.add(
            meetingService.contentShareService.contentShareEvent
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ contentShareEvent: ContentShareEvent ->
                    Log.i(
                        TAG,
                        "Content share event is $contentShareEvent"
                    )
                },
                    { err: Throwable ->
                        Log.e(
                            TAG,
                            "Error in content share events subscription" + err.message
                        )
                    })
        )
    }

    private fun subscribeForModeratorWaitingRoomEvents() {
        if (SampleApplication.blueJeansSDK.blueJeansClient.meetingSession!!.isModerator && !isSubscribeToWaitingRoomEvents) {
            disposable.add(
                meetingService.moderatorWaitingRoomService.isWaitingRoomEnabled
                    .subscribe(
                        SampleApplication.blueJeansSDK.blueJeansClient.bjnScheduler.applyUIScheduler(),
                        {
                            it?.let { isChecked ->
                                isWaitingRoomEnabled = isChecked
                                bottomSheetFragment = MenuFragment(mIOptionMenuCallback, isWaitingRoomEnabled)
                            }
                        }, {
                            Timber.tag(TAG).e(it.message)
                        }
                    )
            )

            disposable.add(
                meetingService.moderatorWaitingRoomService.waitingRoomParticipantEvents
                    .subscribe(
                        SampleApplication.blueJeansSDK.blueJeansClient.bjnScheduler.applyUIScheduler(),
                        {
                            when (it) {
                                is WaitingRoomParticipantEvent.Added -> {
                                    if (it.participants.size == 1) {
                                        showToastMessage("${it.participants[0].name} has arrived in the waiting room")
                                    } else if (it.participants.size > 1) {
                                        showToastMessage("Multiple participants have arrived in the waiting room")
                                    }
                                }
                                is WaitingRoomParticipantEvent.Removed -> {
                                    if (it.participants.size == 1) {
                                        showToastMessage("${it.participants[0].name} has left the waiting room")
                                    } else if (it.participants.size > 1) {
                                        showToastMessage("Multiple participants have left the waiting room")
                                    }
                                }
                                else -> Timber.tag(TAG).i("Unrecognized event")
                            }
                        }, {
                            Timber.tag(TAG).e(it.message)
                        }
                    )
            )
        }
    }

    private fun subscribeForVideoDevices() {
        disposable.add(
            videoDeviceService.videoDevices.subscribeOnUI({ videoDevices ->
                if (videoDevices != null) {
                    videoDeviceAdapter?.let {
                        it.clear()
                        it.addAll(videoDevices)
                        it.notifyDataSetChanged()
                    }
                }
            }
            ) {
                Timber.tag(TAG).e("Error in video devices subscription $it")
            })
    }

    private fun subscribeForClosedCaptionText() {
        disposable.add(meetingService.closedCaptioningService.closedCaptionText.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                binding.tvClosedCaption.text = it
            }, {
                Log.e(TAG,"Error closed caption subscription $it")
            }))
    }

    private fun subscribeForClosedCaptionState() {
        disposable.add(
            meetingService.closedCaptioningService.closedCaptioningState
                .subscribeOnUI({
                    if (it == ClosedCaptioningService.ClosedCaptioningState.Started) {
                        bottomSheetFragment?.updateClosedCaptionSwitchState(true)
                        binding.tvClosedCaption.visible()
                    } else {
                        bottomSheetFragment?.updateClosedCaptionSwitchState(false)
                        binding.tvClosedCaption.gone()
                    }
                }, { Log.e(TAG, "Exception in subscribeForClosedCaptionState ${it.message}") })
        )
    }

    private fun initViews() {
        val pagerAdapter = ScreenSlidePagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.registerOnPageChangeCallback(PagerChangeCallback())
        // we are caching the fragment as when user change layout
        // if not cached, fragment dimensions are returned null resulting in no layout being displayed
        // user can also use detatch and attach fragments on page listener inorder to relayout.
        binding.viewPager.offscreenPageLimit = 1
        TabLayoutMediator(
            binding.tabLayout,
            binding.viewPager
        ) { _: TabLayout.Tab?, _: Int -> }.attach()

        val btnJoin = findViewById<Button>(R.id.btn_join)
        btnJoin.setOnClickListener(this)

        val btnExitWaitingRoom = findViewById<Button>(R.id.btn_exit_waiting_room)
        btnExitWaitingRoom.setOnClickListener(this)

        binding.imgClose.setOnClickListener(this)
        binding.selfView.ivMic.setOnClickListener(this)
        binding.selfView.ivVideo.setOnClickListener(this)
        binding.selfView.ivCameraSettings.setOnClickListener(this)
        binding.imgMenuOption.setOnClickListener(this)
        binding.imgRoster.setOnClickListener(this)
        binding.imgScreenShare.setOnClickListener(this)
        binding.imgUploadLogs.setOnClickListener(this)


        bottomSheetFragment = MenuFragment(mIOptionMenuCallback, isWaitingRoomEnabled)
        participantListFragment = ParticipantListFragment()
        videoLayoutAdapter = getVideoLayoutAdapter()
        videoDeviceAdapter = getVideoDeviceAdapter(ArrayList())
        audioDeviceAdapter = getAudioDeviceAdapter(ArrayList())
        binding.tvAppVersion.text = appVersionString
    }

    private fun showJoiningInProgressView() {
        Timber.tag(TAG).i("showJoiningInProgressView")
        binding.tvAppVersion.gone()
        binding.joinInfo.joinInfo.gone()
        binding.tvProgressMsg.visible()
        binding.tvProgressMsg.text = getString(R.string.connectingState)
        binding.imgClose.visible()
        binding.imgMenuOption.visible()
        binding.controlPanelContainer.setBackgroundResource(R.drawable.meeting_controls_panel_bg)
        binding.imgUploadLogs.gone()
    }

    private fun showInMeetingView() {
        Timber.tag(TAG).i("showInMeetingView")
        if (isInWaitingRoom) {
            isInWaitingRoom = false
            showWaitingRoomUI()
        }
        binding.tvAppVersion.gone()
        binding.tvProgressMsg.gone()
        binding.joinInfo.joinInfo.gone()
        binding.viewPager.visible()
        binding.tabLayout.visible()
        binding.imgClose.visible()
        binding.imgMenuOption.visible()
        binding.imgRoster.visible()
        binding.controlPanelContainer.setBackgroundResource(R.drawable.meeting_controls_panel_bg)
        binding.imgUploadLogs.gone()
        binding.tvClosedCaption.visible()
    }

    private fun showOutOfMeetingView() {
        Timber.tag(TAG).i("showOutOfMeetingView")
        binding.tvAppVersion.visible()
        binding.viewPager.gone()
        binding.viewPager.currentItem = 0
        binding.tabLayout.gone()
        binding.tvProgressMsg.gone()
        binding.imgClose.gone()
        binding.imgMenuOption.gone()
        binding.joinInfo.joinInfo.visible()
        binding.imgRoster.gone()
        binding.imgScreenShare.gone()
        binding.controlPanelContainer.setBackgroundResource(0)
        binding.imgUploadLogs.visible()
        binding.tvClosedCaption.gone()
        binding.tvClosedCaption.text = null
        if (bottomSheetFragment?.isAdded == true) with(bottomSheetFragment) { this?.dismiss() }
        if (participantListFragment?.isAdded == true) {
            supportFragmentManager.beginTransaction().remove(participantListFragment!!).commit()
            val fragment = supportFragmentManager.findFragmentByTag("ParticipantListFragment")
            if (fragment != null) {
                supportFragmentManager.beginTransaction().remove(fragment).commit()
            }
        }
        closeCameraSettings()
        if (audioDeviceDialog?.isShowing == true) audioDeviceDialog?.dismiss()
        if (videoDeviceDialog?.isShowing == true) videoDeviceDialog?.dismiss()
        if (videoLayoutDialog?.isShowing == true) videoLayoutDialog?.dismiss()
    }

    private fun showProgress(msg: String) {
        binding.tvProgressMsg.visible()
        binding.tvProgressMsg.text = msg
    }

    private fun hideProgress() {
        binding.tvProgressMsg.gone()
        binding.tvProgressMsg.text = ""
    }

    private fun toggleAudioMuteUnMuteView(isMuted: Boolean) {
        val resID = if (isMuted) R.drawable.ic_mic_off_black_24dp else R.drawable.ic_mic_black_24dp
        binding.selfView.ivMic.setImageResource(resID)
    }

    private fun toggleVideoMuteUnMuteView(isMuted: Boolean) {
        val resID = if (isMuted) R.drawable.ic_videocam_off_black_24dp
        else R.drawable.ic_videocam_black_24dp
        binding.selfView.ivVideo.setImageResource(resID)
        if (isMuted) {
            closeCameraSettings()
            binding.selfView.ivCameraSettings.gone()
        } else {
            binding.selfView.ivCameraSettings.visible()
        }
    }

    private fun areInMeetingServicesAvailable(): Boolean {
        return meetingService.meetingState.value is
                MeetingService.MeetingState.Connecting ||
                meetingService.meetingState.value is MeetingService.MeetingState.Connected ||
                meetingService.meetingState.value is MeetingService.MeetingState.Reconnecting
    }

    private fun closeCameraSettings() {
        zoomScaleFactor = 1
        if (cameraSettingsDialog?.isShowing == true) {
            cameraSettingsDialog?.dismiss()
        }
    }

    private fun configurePortraitView() {
        val params = binding.selfView.selfView.layoutParams as ConstraintLayout.LayoutParams
        params.startToStart = R.id.parent_layout
        params.topToTop = R.id.parent_layout
        params.endToEnd = R.id.parent_layout
        params.dimensionRatio = resources.getString(R.string.self_view_ratio)
        binding.selfView.selfView.requestLayout()
    }

    private fun configureLandscapeView() {
        val params = binding.selfView.selfView.layoutParams as ConstraintLayout.LayoutParams
        params.startToStart = ConstraintLayout.LayoutParams.UNSET
        params.topToTop = R.id.parent_layout
        params.endToEnd = R.id.parent_layout
        params.dimensionRatio = resources.getString(R.string.self_view_ratio)
        binding.selfView.selfView.requestLayout()
    }

    private fun handleRemoteContentState() {
        if (binding.viewPager.visibility == View.VISIBLE) {
            when {
                isRemoteContentAvailable -> hideProgress()
                else -> showProgress("No one is sharing the remote content.")
            }
        }
    }

    private fun handleVideoState() {
        Timber.tag(TAG).i("handleVideoState videoState = $videoState")
        if (binding.viewPager.visibility == View.VISIBLE) {
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
    }

    private fun handleMeetingState(state: MeetingService.MeetingState) {
        when (state) {
            is MeetingService.MeetingState.Connected -> {
                // add this flag to avoid screen shots.
                // This also allows protection of screen during screen casts from 3rd party apps.
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                OnGoingMeetingService.startService(this)
                subscribeForModeratorWaitingRoomEvents()
                isSubscribeToWaitingRoomEvents = true
            }
            is MeetingService.MeetingState.Idle -> {
                endMeeting()
                isInWaitingRoom = false
                showWaitingRoomUI()
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            is MeetingService.MeetingState.WaitingRoom -> {
                Timber.tag(TAG).i("Moving to WR")
                showOutOfMeetingView()
                isInWaitingRoom = true
                showWaitingRoomUI()
            }
            is MeetingService.MeetingState.Connecting -> {
                meetingService.setAudioMuted(isAudioMuted)
                meetingService.setVideoMuted(isVideoMuted)
                videoDeviceService.enableSelfVideoPreview(false)
                isInWaitingRoom = false
                showWaitingRoomUI()
                showInMeetingView()
                binding.joinInfo.joinInfo.visibility = View.GONE
                binding.tvProgressMsg.visibility = View.VISIBLE
                binding.tvProgressMsg.text = "Connecting..."
            }
            else -> Log.i(TAG, "Unknown meeting state: $state")
        }
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

    private val mIOptionMenuCallback = object : MenuFragment.IMenuCallback {
        override fun showVideoLayoutView(videoLayoutName: String) {
            updateCurrentVideoLayoutForAlertDialog(videoLayoutName)
            showVideoLayoutDialog()
        }

        override fun showAudioDeviceView() {
            showAudioDeviceDialog()
        }

        override fun showVideoDeviceView() {
            showVideoDeviceDialog()
        }

        override fun handleClosedCaptionSwitchEvent(isChecked: Boolean) {
            if (isChecked) {
                meetingService.closedCaptioningService.startClosedCaptioning()
                binding.tvClosedCaption.visible()
            } else {
                meetingService.closedCaptioningService.stopClosedCaptioning()
                binding.tvClosedCaption.gone()
            }
        }
        
        override fun showWaitingRoom() {
            showWaitingRoomDialog()
        }

        override fun setWaitingRoomEnabled(enabled: Boolean) {
            meetingService.moderatorWaitingRoomService.setWaitingRoomEnabled(enabled)
        }
    }

    private fun videoLayoutOptionList(): List<String> {
        return listOf(
            getString(R.string.people_view),
            getString(R.string.speaker_view),
            getString(R.string.gallery_view)
        )
    }

    private fun showVideoLayoutDialog() {
        videoLayoutDialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.video_layouts))
            .setAdapter(videoLayoutAdapter) { _, which -> selectVideoLayout(which) }.create()
        videoLayoutDialog?.show()
    }

    private fun showAudioDeviceDialog() {
        audioDeviceDialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.audio_devices))
            .setAdapter(audioDeviceAdapter) { _, which -> selectAudioDevice(which) }.create()
        audioDeviceDialog?.show()
    }

    private fun showVideoDeviceDialog() {
        videoDeviceDialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.video_devices))
            .setAdapter(videoDeviceAdapter) { _, position -> selectVideoDevice(position) }
            .create()
        videoDeviceDialog?.show()
    }

    private fun showWaitingRoomDialog() {
        val waitingRoomParticipants = meetingService.moderatorWaitingRoomService.waitingRoomParticipants
        if (!waitingRoomParticipants.isNullOrEmpty()) {
            WaitingRoomDialog.newInstance(waitingRoomParticipants, meetingService)
                .show(supportFragmentManager, WaitingRoomDialog.TAG)
        } else {
            showToastMessage("There are no participants in waiting room")
        }
    }

    private fun updateCurrentVideoDeviceForAlertDialog(videoDevice: VideoDevice) {
        val videoDevices = videoDeviceService.videoDevices.value
        videoDevices?.let {
            videoDeviceAdapter?.updateSelectedPosition(it.indexOf(videoDevice))
        }
    }

    private fun updateCurrentAudioDeviceForAlertDialog(currentAudioDevice: AudioDevice) {
        val audioDevices = meetingService.audioDeviceService.audioDevices.value
        audioDevices?.let {
            audioDeviceAdapter!!.updateSelectedPosition(it.indexOf(currentAudioDevice))
        }
    }

    private fun updateCurrentVideoLayoutForAlertDialog(videoLayoutName: String) {
        videoLayoutAdapter?.updateSelectedPosition(
            videoLayoutOptionList().indexOf(videoLayoutName)
        )
    }

    private fun getVideoLayoutAdapter() =
        MenuItemAdapter(
            this, android.R.layout.select_dialog_singlechoice,
            videoLayoutOptionList()
        ) { view, item, _ ->
            (view as CheckedTextView).text = item
        }

    private fun getVideoDeviceAdapter(videoDevices: List<VideoDevice>) =
        MenuItemAdapter(
            this,
            android.R.layout.select_dialog_singlechoice, videoDevices
        ) { view, item, _ ->
            (view as CheckedTextView).text = item.name
        }

    private fun getAudioDeviceAdapter(audioDevices: List<AudioDevice>) =
        MenuItemAdapter(
            this,
            android.R.layout.select_dialog_singlechoice, audioDevices
        ) { view, item, _ ->
            (view as CheckedTextView).text = getAudioDeviceName(item)
        }

    private fun setVideoLayout(videoLayoutName: String?) {
        val videoLayout = when (videoLayoutName) {
            getString(R.string.people_view) ->
                MeetingService.VideoLayout.People

            getString(R.string.gallery_view) ->
                MeetingService.VideoLayout.Gallery

            getString(R.string.speaker_view) ->
                MeetingService.VideoLayout.Speaker

            else -> throw IllegalArgumentException("Invalid video layout")
        }
        bottomSheetFragment?.updateVideoLayout(videoLayoutName)
        meetingService.setVideoLayout(videoLayout)
    }

    private fun selectAudioDevice(position: Int) {
        audioDeviceAdapter?.updateSelectedPosition(position)
        audioDeviceAdapter?.getItem(position)?.let {
            bottomSheetFragment?.updateAudioDevice(getAudioDeviceName(it))
            meetingService.audioDeviceService.selectAudioDevice(it)
        }
    }

    private fun selectVideoDevice(position: Int) {
        videoDeviceAdapter?.updateSelectedPosition(position)
        videoDeviceAdapter?.getItem(position)?.let {
            bottomSheetFragment?.updateVideoDevice(it.name)
            closeCameraSettings()
            videoDeviceService.selectVideoDevice(it)
        }
    }

    private fun selectVideoLayout(position: Int) {
        videoLayoutAdapter?.updateSelectedPosition(position)
        setVideoLayout(videoLayoutAdapter?.getItem(position))
    }

    private fun hideKeyboard() {
        val view = this.currentFocus
        if (view != null) {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    private fun showCameraSettingsDialog() {
        val seek = SeekBar(this).apply {
            max = 10
            min = 1
            progress = zoomScaleFactor
        }
        seek.setOnSeekBarChangeListener(zoomSliderChangeListener)
        cameraSettingsDialog = AlertDialog.Builder(this).apply {
            setTitle(getString(R.string.camera_setting_title))
            setView(seek)
        }.create()
        cameraSettingsDialog?.show()
    }

    /**
     * It calculates the new crop region by finding out the delta between active camera region's
     * x and y coordinates and divide by zoom scale factor to get updated camera's region.
     * @param cameraActiveRegion
     * @param zoomFactor
     * @return Rect coordinates of crop region to be zoomed.
     */
    private fun getCropRegionForZoom(
        cameraActiveRegion: Rect,
        zoomFactor: Int
    ): Rect {
        val xCenter = cameraActiveRegion.width() / 2
        val yCenter = cameraActiveRegion.height() / 2
        val xDelta = (0.5f * cameraActiveRegion.width() / zoomFactor).toInt()
        val yDelta = (0.5f * cameraActiveRegion.height() / zoomFactor).toInt()
        return Rect(
            xCenter - xDelta, yCenter - yDelta, xCenter + xDelta,
            yCenter + yDelta
        )
    }

    private fun getVideoLayoutDisplayName(videoLayout: MeetingService.VideoLayout): String {
        return when (videoLayout) {
            MeetingService.VideoLayout.Speaker -> {
                getString(R.string.speaker_view)
            }
            MeetingService.VideoLayout.Gallery -> {
                getString(R.string.gallery_view)
            }
            MeetingService.VideoLayout.People -> {
                getString(R.string.people_view)
            }
        }
    }

    private val zoomSliderChangeListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            zoomScaleFactor = progress
            val activeRegion = characteristics
                .get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            val cropRegion = activeRegion?.let { getCropRegionForZoom(it, progress) }
            videoDeviceService.setRepeatingCaptureRequest(
                CaptureRequest.SCALER_CROP_REGION, cropRegion
            )
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {
        }

        override fun onStopTrackingTouch(seekBar: SeekBar?) {
        }
    }


    /** Detects, characterizes, and connects to a CameraDevice (used for all camera operations) */
    private val cameraManager: CameraManager by lazy {
        val context = applicationContext
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    /** [CameraCharacteristics] corresponding to the provided Camera ID */
    private val characteristics: CameraCharacteristics by lazy {
        val cameraId: String = videoDeviceService.currentVideoDevice.value!!.id
        cameraManager.getCameraCharacteristics(cameraId)
    }

    private val userName: String by lazy {
        when {
            binding.joinInfo.etName.text.toString().isEmpty() -> "Guest"
            else -> binding.joinInfo.etName.text.toString()
        }
    }

    private fun showWaitingRoomUI() {
        if (isInWaitingRoom) {
            binding.joinInfo.joinInfo.visibility = View.GONE
            binding.waitingRoomLayout.clWaitingRoom.visibility = View.VISIBLE
        } else {
            binding.joinInfo.joinInfo.visibility = View.VISIBLE
            binding.waitingRoomLayout.clWaitingRoom.visibility = View.GONE
        }
        hideProgress()
    }



    companion object {
        const val TAG = "MainActivity"
    }
}