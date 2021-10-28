/*
 * Copyright (c) 2020 Blue Jeans Network, Inc. All rights reserved.
 */
package com.bluejeans.android.sdksample;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import com.bjnclientcore.inmeeting.contentshare.ContentShareType;
import com.bluejeans.android.sdksample.dialog.WaitingRoomDialog;
import com.bluejeans.android.sdksample.menu.MenuFragment;
import com.bluejeans.android.sdksample.menu.MenuFragment.IMenuCallback;
import com.bluejeans.android.sdksample.menu.adapters.AudioDeviceAdapter;
import com.bluejeans.android.sdksample.menu.adapters.VideoDeviceAdapter;
import com.bluejeans.android.sdksample.menu.adapters.VideoLayoutAdapter;
import com.bluejeans.android.sdksample.participantlist.ParticipantListFragment;
import com.bluejeans.android.sdksample.viewpager.ScreenSlidePagerAdapter;
import com.bluejeans.bluejeanssdk.devices.AudioDevice;
import com.bluejeans.bluejeanssdk.devices.VideoDevice;
import com.bluejeans.bluejeanssdk.devices.VideoDeviceService;
import com.bluejeans.bluejeanssdk.logging.LoggingService;
import com.bluejeans.bluejeanssdk.meeting.ClosedCaptioningService;
import com.bluejeans.bluejeanssdk.meeting.ContentShareAvailability;
import com.bluejeans.bluejeanssdk.meeting.ContentShareState;
import com.bluejeans.bluejeanssdk.meeting.MeetingService;
import com.bluejeans.bluejeanssdk.meeting.ParticipantsService;
import com.bluejeans.bluejeanssdk.meeting.WaitingRoomParticipantEvent;
import com.bluejeans.bluejeanssdk.permission.PermissionService;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import kotlin.Unit;
import static com.bluejeans.android.sdksample.utils.AudioDeviceHelper.getAudioDeviceName;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    public static final String TAG = "MainActivity";

    private final String appVersionString = "v" + SampleApplication.getBlueJeansSDK().getVersion();
    private final PermissionService mPermissionService = SampleApplication.getBlueJeansSDK().getPermissionService();
    private final LoggingService mLoggingService = SampleApplication.getBlueJeansSDK().getLoggingService();
    private final MeetingService mMeetingService = SampleApplication.getBlueJeansSDK().getMeetingService();
    private final VideoDeviceService mVideoDeviceService = SampleApplication.getBlueJeansSDK().getVideoDeviceService();

    private final CompositeDisposable mDisposable = new CompositeDisposable();
    private final CompositeDisposable mJoinMeetingDisposable = new CompositeDisposable();
    private boolean mIsAudioMuted, mIsVideoMuted;
    private MeetingService.VideoState mVideoState;
    private boolean mIsRemoteContentAvailable;

    //View IDs
    private ConstraintLayout mSelfView, mJoinLayout, mWaitingRoomLayout;
    private Button btnJoin, btnExitWaitingRoom;
    private ViewPager2 mViewPager;
    private TabLayout mTabLayout;
    private ImageView mIvMic, mIvClose, mIvMenuOption, mIvLogUploadButton;
    private ImageView mIvVideo;
    private EditText mEtEventId, mEtPassCode, mEtName;
    private TextView mTvProgressMsg, mAppVersion, mTvClosedCaption;
    private ConstraintLayout mControlPanelContainer;
    private ImageView mIvParticipant;
    private ImageView mIvScreenShare;
    private ImageView mCameraSettings;
    private MenuFragment mBottomSheetFragment;
    private ParticipantListFragment mParticipantListFragment = null;

    //For alter dialog
    private VideoDeviceAdapter mVideoDeviceAdapter = null;
    private AudioDeviceAdapter mAudioDeviceAdapter = null;
    private VideoLayoutAdapter mVideoLayoutAdapter = null;
    private AlertDialog mAudioDialog = null;
    private AlertDialog mVideoLayoutDialog = null;
    private AlertDialog mVideoDeviceDialog = null;
    private AlertDialog mUploadLogsDialog = null;
    private AlertDialog mCameraSettingsDialog = null;
    private ProgressBar mProgressBar = null;
    private int mZoomScaleFactor = 1; // default value of 1, no zoom to start with
    private boolean isInWaitingRoom = false;
    private boolean isWaitingRoomEnabled = false;
    private boolean isScreenShareInProgress = false;
    private boolean isSubscribeToWaitingRoomEvents = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // permission service needs activity to be registered before calling request for permissions
        mPermissionService.register(this);
        initViews();
        checkCameraPermissionAndStartSelfVideo();
        activateSDKSubscriptions();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            configurePortraitView();
        } else {
            configureLandscapeView();
        }
        Log.d(TAG, "onConfigurationChanged");

        /* The multi-stream remote video fragment computes size at run-time, when handling config change and using
        viewpager2, we need to make sure video fragment or video fragment's parent is visible on Config change inorder to
        propagate dimensions at runtime.*/
        mViewPager.setCurrentItem(0);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!isScreenShareInProgress) {
            MeetingService.MeetingState meetingState = mMeetingService.getMeetingState().getValue();
            handleMeetingState(meetingState);
        }
    }

    @Override
    protected void onDestroy() {
        mDisposable.dispose();
        mJoinMeetingDisposable.clear();
        mBottomSheetFragment = null;
        super.onDestroy();
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btnJoin:
                checkMinimumPermissionsAndJoin();
                break;
            case R.id.imgClose:
                mMeetingService.endMeeting();
                mVideoDeviceService.enableSelfVideoPreview(!mIsVideoMuted);
                endMeeting();
                break;
            case R.id.imgMenuOption:
                mBottomSheetFragment.show(getSupportFragmentManager(), mBottomSheetFragment.getTag());
                break;
            case R.id.ivMic:
                mIsAudioMuted = !mIsAudioMuted;
                mMeetingService.setAudioMuted(mIsAudioMuted);
                toggleAudioMuteUnMuteView(mIsAudioMuted);
                break;
            case R.id.ivVideo:
                mIsVideoMuted = !mIsVideoMuted;
                if (areInMeetingServicesAvailable()) {
                    mMeetingService.setVideoMuted(mIsVideoMuted);
                } else {
                    mVideoDeviceService.enableSelfVideoPreview(!mIsVideoMuted);
                }
                toggleVideoMuteUnMuteView(mIsVideoMuted);
                break;
            case R.id.imgRoster:
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.rosterContainer, mParticipantListFragment)
                        .addToBackStack("ParticipantListFragment")
                        .commit();
                break;
            case R.id.imgScreenShare:
                if (mMeetingService.getContentShareService().getContentShareState().getValue() instanceof ContentShareState.Stopped) {
                    isScreenShareInProgress = true;
                    MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                    activityResultLauncher.launch(mediaProjectionManager.createScreenCaptureIntent());
                } else {
                    isScreenShareInProgress = false;
                    mMeetingService.getContentShareService().stopContentShare();
                }
                break;
            case R.id.ivCameraSettings:
                showCameraSettingsDialog();
                break;

            case R.id.imgUploadLogs:
                showUploadLogsDialog();
                break;

            case R.id.btn_exit_waiting_room:
                mMeetingService.endMeeting();
                endMeeting();
                break;
            default:
        }
    }

    private void showUploadLogsDialog() {
        mUploadLogsDialog = new AlertDialog.Builder(this)
                .setView(R.layout.submit_log_dialog).create();
        mUploadLogsDialog.setCanceledOnTouchOutside(true);
        mUploadLogsDialog.show();
        EditText editText = mUploadLogsDialog.findViewById(R.id.description);
        Button submitButton = mUploadLogsDialog.findViewById(R.id.btn_submit);
        mProgressBar = mUploadLogsDialog.findViewById(R.id.progressBar);
        if (editText != null) {
            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (submitButton != null) {
                        submitButton.setEnabled(count != 0);
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        }
        if (submitButton != null) {
            submitButton.setOnClickListener(v -> {
                String description = Objects.requireNonNull(editText).getText().toString();
                submitButton.setEnabled(false);
                mProgressBar.setVisibility(View.VISIBLE);
                uploadLogs(description);
            });
        }
    }

    private void uploadLogs(String comments) {
        if (!TextUtils.isEmpty(comments)) {
            String userName = (TextUtils.isEmpty(mEtName.getText().toString()) ? "Guest"
                    : mEtName.getText().toString());
            mDisposable.add(
                    mLoggingService.uploadLog(comments, userName)
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(result ->
                                    {
                                        Log.i(TAG, "Log uploaded successfully " + result);
                                        if (result != LoggingService.LogUploadResult.Success.INSTANCE) {
                                            showToastMessage(getString(R.string.upload_logs_failure));
                                        } else {
                                            showToastMessage(getString(R.string.upload_logs_success));
                                        }
                                        mUploadLogsDialog.dismiss();
                                    },
                                    error -> {
                                        Log.e(TAG, "Error while uploading logs");
                                        mProgressBar.setVisibility(View.GONE);
                                        showToastMessage(getString(R.string.upload_logs_failure));
                                    }));
        } else {
            showToastMessage("Please enter your comments.");
        }
    }

    private void showToastMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private final ActivityResultLauncher<Intent> activityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent data = result.getData();
                    if (data != null) {
                        mMeetingService.getContentShareService()
                                .startContentShare(new ContentShareType.Screen(data));
                    }
                }
            });

    private void activateSDKSubscriptions() {
        subscribeForMeetingStatus();
        subscribeToWaitingRoomEvents();
        subscribeForVideoMuteStatus();
        subscribeForAudioMuteStatus();
        subscribeForVideoState();
        subscribeForRemoteContentState();
        subscribeForVideoLayout();
        subscribeForAudioDevices();
        subscribeForCurrentAudioDevice();
        subscribeForVideoDevices();
        subscribeForCurrentVideoDevice();
        subscribeForParticipants();
        subscribeForContentShareState();
        subscribeForContentShareAvailability();
        subscribeForContentShareEvents();
        subscribeForClosedCaptionText();
        subscribeForClosedCaptionState();
    }
    
    private void checkCameraPermissionAndStartSelfVideo() {
        if (mPermissionService.hasPermission(PermissionService.Permission.Camera.INSTANCE)) {
            startSelfVideo();
        } else {
            PermissionService.Permission[] arr = {PermissionService.Permission.Camera.INSTANCE};
            mDisposable.add(mPermissionService.requestPermissions(arr).subscribe(
                    grantedStatus -> {
                        if (grantedStatus == PermissionService.RequestStatus.Granted.INSTANCE) {
                            startSelfVideo();
                        } else {
                            Log.d(TAG, "Camera permission denied");
                        }
                    },
                    err -> Log.e(TAG, "Error in requesting permission subscription")));
        }
    }

    private void startSelfVideo() {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.selfViewFrame, mVideoDeviceService.getSelfVideoFragment())
                .commit();
        mVideoDeviceService.enableSelfVideoPreview(true);
    }

    private void checkMinimumPermissionsAndJoin() {
        if (mPermissionService.hasMinimumPermissions()) {
            hideKeyboard();
            joinMeeting();
        } else {
            requestMinimumPermissionsAndJoin();
        }
    }

    private void requestMinimumPermissionsAndJoin() {
        mDisposable.add(mPermissionService.requestAllPermissions().subscribe(
                areAllPermissionsGranted -> {
                    if (areAllPermissionsGranted == PermissionService.RequestStatus.Granted.INSTANCE) {
                        joinMeeting();
                    } else {
                        Log.i(TAG, "Not enough permissions to join a meeting");
                    }
                },
                err -> Log.e(TAG, "Error in requesting permissions subscription " + err.getMessage())));
    }

    private void joinMeeting() {
        String meetingId = mEtEventId.getText().toString();
        String passcode = mEtPassCode.getText().toString();
        String name = (TextUtils.isEmpty(mEtName.getText().toString()) ? "AndroidSDK"
                : mEtName.getText().toString());
        showJoiningInProgressView();
        mJoinMeetingDisposable.clear();
        mJoinMeetingDisposable.add(mMeetingService.joinMeeting(
                new MeetingService.JoinParams
                        (meetingId, passcode, name))
                .subscribeOn(Schedulers.single())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        result -> {
                            Log.i(TAG, "Join state is " + result);
                            if (result != MeetingService.JoinResult.Success.INSTANCE) {
                                showOutOfMeetingView();
                            }
                        },
                        error -> showOutOfMeetingView()));

    }

    private void endMeeting() {
        if (isInWaitingRoom) {
            isInWaitingRoom = false;
            showWaitingRoomUI();
        } else {
            showOutOfMeetingView();
        }

        if (mCameraSettingsDialog != null)
            mCameraSettingsDialog.dismiss();
        OnGoingMeetingService.stopService(this);
        mJoinMeetingDisposable.clear();
    }

    // Return Unit.INSTANCE; is needed for a kotlin java interop
    // Refer https://developer.android.com/kotlin/interop#lambda_arguments for more details
    private void subscribeForMeetingStatus() {
        mDisposable.add(
                mMeetingService.getMeetingState().getRxObservable().observeOn(AndroidSchedulers.mainThread())
                    .subscribe(state -> {
                        Log.d(TAG, "State: " + state);
                        handleMeetingState(state);
                    }, err -> {
                        Log.e(TAG, err.getMessage());
                    })
        );
    }

    private void subscribeToWaitingRoomEvents() {
        mDisposable.add(mMeetingService.getWaitingRoomEvent().observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        event -> {
                            if (event instanceof MeetingService.WaitingRoomEvent.Denied) {
                                showToastMessage("Denied by moderator");
                            } else if (event instanceof MeetingService.WaitingRoomEvent.Demoted) {
                                showToastMessage("Demoted by moderator");
                                mVideoDeviceService.enableSelfVideoPreview(!mIsVideoMuted);
                            } else if (event instanceof MeetingService.WaitingRoomEvent.Admitted) {
                                showToastMessage("Moderator has approved");
                            } else {
                                Log.w(TAG, "Unrecognized event: " + event);
                            }
                        },
                        err -> {
                            Log.e(TAG, err.getMessage());
                        })
        );
    }

    private void subscribeForVideoState() {
        mDisposable.add(mMeetingService.getVideoState().subscribeOnUI(
                state -> {
                    mVideoState = state;
                    if (mVideoState instanceof MeetingService.VideoState.Active) {
                        showInMeetingView();
                    } else {
                        handleVideoState();
                    }
                    return Unit.INSTANCE;
                },
                err -> {
                    Log.e(TAG, "Error in video state subscription" + err.getMessage());
                    return Unit.INSTANCE;
                }));
    }

    private void subscribeForRemoteContentState() {
        mDisposable.add(mMeetingService.getContentShareService().getReceivingRemoteContent().subscribeOnUI(isReceivingRemoteContent -> {
            if (isReceivingRemoteContent != null) {
                mIsRemoteContentAvailable = isReceivingRemoteContent;
                if (isReceivingRemoteContent) {
                    showInMeetingView();
                } else {
                    handleRemoteContentState();
                }
            }
            return Unit.INSTANCE;
        }, err -> {
            Log.e(TAG, "Error in remote content subscription " + err.getMessage());
            return Unit.INSTANCE;
        }));
    }

    private void subscribeForAudioMuteStatus() {
        mDisposable.add(mMeetingService.getAudioMuted().subscribe(
                isMuted -> {
                    if (isMuted != null) {
                        // This could be due to local mute or remote mute
                        toggleAudioMuteUnMuteView(isMuted);
                    }
                    Log.i(TAG, " Audio Mute state " + isMuted);
                    return Unit.INSTANCE;
                },
                err -> {
                    Log.e(TAG, "Error in audio mute status subscription");
                    return Unit.INSTANCE;
                }));
    }

    private void subscribeForVideoMuteStatus() {
        mDisposable.add(mMeetingService.getVideoMuted().subscribeOnUI(
                isMuted -> {
                    if (isMuted != null) {
                        // This could be due to local mute or remote mute
                        toggleVideoMuteUnMuteView(isMuted);
                    }

                    Log.i(TAG, " Video Mute state " + isMuted);
                    return Unit.INSTANCE;
                },
                err -> {
                    Log.e(TAG, "Error in video mute status subscription " + err.getMessage());
                    return Unit.INSTANCE;
                }));
    }

    private void subscribeForVideoLayout() {
        mDisposable.add(mMeetingService.getVideoLayout().subscribeOnUI(
                videoLayout -> {
                    if (videoLayout != null) {
                        String videoLayoutName = null;
                        if (videoLayout.equals(MeetingService.VideoLayout.Speaker.INSTANCE)) {
                            videoLayoutName = getString(R.string.speaker_view);
                        } else if (videoLayout.equals(MeetingService.VideoLayout.Gallery.INSTANCE)) {
                            videoLayoutName = getString(R.string.gallery_view);
                        } else if (videoLayout.equals(MeetingService.VideoLayout.People.INSTANCE)) {
                            videoLayoutName = getString(R.string.people_view);
                        }
                        mBottomSheetFragment.updateVideoLayout(videoLayoutName);
                        updateCurrentVideoLayoutForAlertDialog(videoLayoutName);
                    }
                    return Unit.INSTANCE;
                },
                err -> {
                    Log.e(TAG, "Error in video layout subscription " + err.getMessage());
                    return Unit.INSTANCE;
                }));
    }

    private void subscribeForCurrentAudioDevice() {
        mDisposable.add(mMeetingService.getAudioDeviceService().getCurrentAudioDevice().subscribeOnUI(
                currentAudioDevice -> {
                    if (currentAudioDevice != null) {
                        mBottomSheetFragment.updateAudioDevice(getAudioDeviceName(currentAudioDevice));
                        updateCurrentAudioDeviceForAlertDialog(currentAudioDevice);
                    }
                    return Unit.INSTANCE;
                },
                err -> {
                    Log.e(TAG, "Error in current audio device subscription " + err.getMessage());
                    return Unit.INSTANCE;
                }));

    }

    private void subscribeForAudioDevices() {
        mDisposable.add(mMeetingService.getAudioDeviceService().getAudioDevices().subscribeOnUI(audioDevices -> {
                    if (audioDevices != null) {
                        mAudioDeviceAdapter.clear();
                        mAudioDeviceAdapter.addAll(audioDevices);
                        mAudioDeviceAdapter.notifyDataSetChanged();
                    }
                    return Unit.INSTANCE;
                },
                err -> {
                    Log.e(TAG, "Error in audio devices subscription " + err.getMessage());
                    return Unit.INSTANCE;
                }));
    }

    private void subscribeForCurrentVideoDevice() {
        mDisposable.add(mVideoDeviceService.getCurrentVideoDevice().subscribeOnUI(
                currentVideoDevice -> {
                    if (currentVideoDevice != null) {
                        mBottomSheetFragment.updateVideoDevice(currentVideoDevice.getName());
                        updateCurrentVideoDeviceForAlertDialog(currentVideoDevice);
                    }
                    return Unit.INSTANCE;
                },
                err -> {
                    Log.e(TAG, "Error in current video device subscription " + err.getMessage());
                    return Unit.INSTANCE;
                }));
    }

    private void subscribeForParticipants() {
        mDisposable.add(mMeetingService.getParticipantsService().getParticipants().subscribeOnUI(
                participantList -> {
                    if (mParticipantListFragment != null && participantList != null) {
                        mParticipantListFragment.updateMeetingList(participantList);
                    }
                    return Unit.INSTANCE;
                },
                err -> {
                    Log.e(TAG, "Error in Participants subscription" + err.getMessage());
                    return Unit.INSTANCE;
                }));
    }

    private void subscribeForContentShareAvailability() {
        mDisposable.add(mMeetingService.getContentShareService().getContentShareAvailability().subscribeOnUI(
                contentShareAvailability -> {
                    if (contentShareAvailability instanceof ContentShareAvailability.Available) {
                        mIvScreenShare.setVisibility(View.VISIBLE);
                    } else {
                        mIvScreenShare.setVisibility(View.GONE);
                    }
                    return Unit.INSTANCE;
                },
                err -> {
                    Log.e(TAG, "Error in content share availability" + err.getMessage());
                    return Unit.INSTANCE;
                }));
    }

    private void subscribeForContentShareState() {
        mDisposable.add(mMeetingService.getContentShareService().getContentShareState().subscribeOnUI(
                contentShareState -> {
                    if (contentShareState instanceof ContentShareState.Stopped) {
                        mIvScreenShare.setSelected(false);
                        MeetingNotificationUtility.updateNotificationMessage(this, getString(R.string.meeting_notification_message));
                    } else if (contentShareState != null) {
                        mIvScreenShare.setSelected(true);
                        MeetingNotificationUtility.updateNotificationMessage(this, getString(R.string.screen_share_notification_message));
                    }
                    return Unit.INSTANCE;
                },
                err -> {
                    Log.e(TAG, "Error in content share state subscription" + err.getMessage());
                    return Unit.INSTANCE;
                }));
    }

    private void subscribeForContentShareEvents() {
        mDisposable.add(mMeetingService.getContentShareService().getContentShareEvent()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        contentShareEvent -> Log.i(TAG, "Content share event is " + contentShareEvent),
                        err -> Log.e(TAG, "Error in content share events subscription" + err.getMessage())));
    }

    private void subscribeForVideoDevices() {
        mDisposable.add(mVideoDeviceService.getVideoDevices().subscribeOnUI(videoDevices -> {
                    if (videoDevices != null) {
                        mVideoDeviceAdapter.clear();
                        mVideoDeviceAdapter.addAll(videoDevices);
                        mVideoDeviceAdapter.notifyDataSetChanged();
                    }
                    return Unit.INSTANCE;
                },
                err -> {
                    Log.e(TAG, "Error in video devices subscription");
                    return Unit.INSTANCE;
                }));
    }

    private void subscribeForClosedCaptionText() {
        mDisposable.add(mMeetingService.getClosedCaptioningService().getClosedCaptionText()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(content -> {
                    mTvClosedCaption.setText(content);
                }, error -> Log.e(TAG, "Error closed caption subscription " + error)));
    }

    private void subscribeForClosedCaptionState() {
        mDisposable.add(mMeetingService.getClosedCaptioningService().getClosedCaptioningState().subscribeOnUI(
                closedCaptioningState -> {
                    if (closedCaptioningState != null) {
                        if (closedCaptioningState == ClosedCaptioningService.
                                ClosedCaptioningState.Started.INSTANCE) {
                            mTvClosedCaption.setVisibility(View.VISIBLE);
                            mBottomSheetFragment.updateClosedCaptionSwitchState(true);
                        } else {
                            mBottomSheetFragment.updateClosedCaptionSwitchState(false);
                            mTvClosedCaption.setVisibility(View.GONE);
                        }
                    }
                    return Unit.INSTANCE;
                }, err -> {
                    Log.e(TAG, "Error subscribing closed caption event");
                    return Unit.INSTANCE;
                }
        ));
    }
    
    private void subscribeForModeratorWaitingRoomEvents() {
        boolean isModerator = SampleApplication.getBlueJeansSDK()
                .getBlueJeansClient().getMeetingSession().isModerator();
        if (isModerator && !isSubscribeToWaitingRoomEvents) {
            mDisposable.add(mMeetingService.getModeratorWaitingRoomService().isWaitingRoomEnabled()
                    .subscribe(
                            SampleApplication.getBlueJeansSDK().getBlueJeansClient().bjnScheduler.applyUIScheduler(),
                            isEnabled -> {
                                if (isEnabled == true) {
                                    isWaitingRoomEnabled = true;
                                } else {
                                    isWaitingRoomEnabled = false;
                                }
                                mBottomSheetFragment = new MenuFragment(mIOptionMenuCallback, isWaitingRoomEnabled);
                                return Unit.INSTANCE;
                            }, err -> {
                                Log.e(TAG, "Error subscribing to waiting room enabled");
                                return Unit.INSTANCE;
                            }
                    ));

            mDisposable.add(mMeetingService.getModeratorWaitingRoomService().getWaitingRoomParticipantEvents()
                    .subscribe(
                            SampleApplication.getBlueJeansSDK().getBlueJeansClient().bjnScheduler.applyUIScheduler(),
                            event -> {
                                if (event instanceof WaitingRoomParticipantEvent.Added) {
                                    if (((WaitingRoomParticipantEvent.Added) event).getParticipants().size() == 1) {
                                        showToastMessage(((WaitingRoomParticipantEvent.Added) event).getParticipants().get(0).getName() + " has arrived in the waiting room");
                                    } else if (((WaitingRoomParticipantEvent.Added) event).getParticipants().size() > 1) {
                                        showToastMessage("Multiple participants have arrived in the waiting room");
                                    }
                                } else if (event instanceof WaitingRoomParticipantEvent.Removed) {
                                    if (((WaitingRoomParticipantEvent.Removed) event).getParticipants().size() == 1) {
                                        showToastMessage(((WaitingRoomParticipantEvent.Removed) event).getParticipants().get(0).getName() + " has left the waiting room");
                                    } else if (((WaitingRoomParticipantEvent.Removed) event).getParticipants().size() > 1) {
                                        showToastMessage("Multiple participants have left the waiting room");
                                    }
                                }
                                return Unit.INSTANCE;
                            }, err -> {
                                Log.e(TAG, "Unknown waiting room participant event: " + err.getMessage());
                                return Unit.INSTANCE;
                            }
                    )
            );
        }
    }

    private boolean areInMeetingServicesAvailable() {
        return mMeetingService.getMeetingState().getValue() instanceof MeetingService.MeetingState.Connecting
                || mMeetingService.getMeetingState().getValue() instanceof MeetingService.MeetingState.Connected
                || mMeetingService.getMeetingState().getValue() instanceof MeetingService.MeetingState.Reconnecting;
    }

    private void initViews() {
        mViewPager = findViewById(R.id.viewPager);
        // we are caching the fragment as when user change layout
        // if not cached, fragment dimensions are returned null resulting in no layout being displayed
        // user can also use detach and attach fragments on page listener inorder to relayout.
        mViewPager.setOffscreenPageLimit(1);
        mTabLayout = findViewById(R.id.tabLayout);
        mIvClose = findViewById(R.id.imgClose);
        mIvMenuOption = findViewById(R.id.imgMenuOption);
        mIvParticipant = findViewById(R.id.imgRoster);
        mIvScreenShare = findViewById(R.id.imgScreenShare);
        mCameraSettings = findViewById(R.id.ivCameraSettings);
        mControlPanelContainer = findViewById(R.id.control_panel_container);
        //Self View
        mSelfView = findViewById(R.id.selfView);
        mIvMic = findViewById(R.id.ivMic);
        mIvVideo = findViewById(R.id.ivVideo);
        //Join Layout
        mJoinLayout = findViewById(R.id.joinInfo);
        mEtEventId = findViewById(R.id.etEventId);
        mEtPassCode = findViewById(R.id.etPasscode);
        mEtName = findViewById(R.id.etName);
        // Waiting Room Layout
        mWaitingRoomLayout = findViewById(R.id.waiting_room_layout);
        //Progress View
        mTvProgressMsg = findViewById(R.id.tvProgressMsg);
        mAppVersion = findViewById(R.id.tvAppVersion);
        mTvClosedCaption = findViewById(R.id.tvClosedCaption);
        // Initialize adapter and click listener
        FragmentStateAdapter pagerAdapter = new ScreenSlidePagerAdapter(this);
        mViewPager.setAdapter(pagerAdapter);
        mViewPager.registerOnPageChangeCallback(new PagerChangeCallback());
        new TabLayoutMediator(mTabLayout, mViewPager, (tab, position) -> {
        }).attach();

        btnJoin = findViewById(R.id.btnJoin);
        btnJoin.setOnClickListener(this);
        btnExitWaitingRoom = findViewById(R.id.btn_exit_waiting_room);
        btnExitWaitingRoom.setOnClickListener(this);
        mIvLogUploadButton = findViewById(R.id.imgUploadLogs);
        mIvLogUploadButton.setOnClickListener(this);
        mIvClose.setOnClickListener(this);
        mIvMenuOption.setOnClickListener(this);
        mIvParticipant.setOnClickListener(this);
        mIvScreenShare.setOnClickListener(this);
        mIvMic.setOnClickListener(this);
        mIvVideo.setOnClickListener(this);
        mCameraSettings.setOnClickListener(this);
        mBottomSheetFragment = new MenuFragment(mIOptionMenuCallback, isWaitingRoomEnabled);
        mParticipantListFragment = new ParticipantListFragment();
        mVideoLayoutAdapter = getVideoLayoutAdapter();
        mVideoDeviceAdapter = getVideoDeviceAdapter(new ArrayList<>());
        mAudioDeviceAdapter = getAudioDeviceAdapter(new ArrayList<>());
        mAppVersion.setText(appVersionString);
    }

    private void showJoiningInProgressView() {
        mJoinLayout.setVisibility(View.GONE);
        mTvProgressMsg.setVisibility(View.VISIBLE);
        mTvProgressMsg.setText(getString(R.string.connectingState));
        mAppVersion.setVisibility(View.GONE);
        mIvClose.setVisibility(View.VISIBLE);
        mIvMenuOption.setVisibility(View.VISIBLE);
        mIvLogUploadButton.setVisibility(View.GONE);
        mControlPanelContainer.setBackgroundResource(R.drawable.meeting_controls_panel_bg);
    }

    private void showInMeetingView() {
        mAppVersion.setVisibility(View.GONE);
        mTvProgressMsg.setVisibility(View.GONE);
        mJoinLayout.setVisibility(View.GONE);
        mViewPager.setVisibility(View.VISIBLE);
        mTabLayout.setVisibility(View.VISIBLE);
        mIvClose.setVisibility(View.VISIBLE);
        mIvMenuOption.setVisibility(View.VISIBLE);
        mIvParticipant.setVisibility(View.VISIBLE);
        mIvLogUploadButton.setVisibility(View.GONE);
        mTvClosedCaption.setVisibility(View.VISIBLE);
        mControlPanelContainer.setBackgroundResource(R.drawable.meeting_controls_panel_bg);
    }

    private void showOutOfMeetingView() {
        mViewPager.setVisibility(View.GONE);
        mViewPager.setCurrentItem(0);
        mTabLayout.setVisibility(View.GONE);
        mTvProgressMsg.setVisibility(View.GONE);
        mIvClose.setVisibility(View.GONE);
        mIvMenuOption.setVisibility(View.GONE);
        mJoinLayout.setVisibility(View.VISIBLE);
        mIvParticipant.setVisibility(View.GONE);
        mIvScreenShare.setVisibility(View.GONE);
        mAppVersion.setVisibility(View.VISIBLE);
        mIvLogUploadButton.setVisibility(View.VISIBLE);
        mControlPanelContainer.setBackgroundResource(0);
        mTvClosedCaption.setVisibility(View.GONE);
        mTvClosedCaption.setText(null);
        if (mBottomSheetFragment != null && mBottomSheetFragment.isAdded()) {
            mBottomSheetFragment.dismiss();
        }
        if (mParticipantListFragment != null && mParticipantListFragment.isAdded()) {
            getSupportFragmentManager().beginTransaction().remove(mParticipantListFragment).commit();
            Fragment fragment = getSupportFragmentManager().findFragmentByTag("ParticipantListFragment");
            if (fragment != null) {
                getSupportFragmentManager().beginTransaction().remove(fragment).commit();
            }
        }
        closeCameraSettings();
        if (mAudioDialog != null && mAudioDialog.isShowing()) {
            mAudioDialog.dismiss();
        }
        if (mVideoDeviceDialog != null && mVideoDeviceDialog.isShowing()) {
            mVideoDeviceDialog.dismiss();
        }
        if (mVideoLayoutDialog != null && mVideoLayoutDialog.isShowing()) {
            mVideoLayoutDialog.dismiss();
        }
    }

    private void showProgress(String msg) {
        mTvProgressMsg.setVisibility(View.VISIBLE);
        mTvProgressMsg.setText(msg);
    }

    private void hideProgress() {
        mTvProgressMsg.setVisibility(View.GONE);
        mTvProgressMsg.setText("");
    }

    private void toggleAudioMuteUnMuteView(boolean isMuted) {
        int resID = isMuted ? R.drawable.mic_off_black : R.drawable.mic_on_black;
        mIvMic.setImageResource(resID);
    }

    private void toggleVideoMuteUnMuteView(boolean isMuted) {
        int resID = isMuted ? R.drawable.videocam_off_black : R.drawable.videocam_on_black;
        mIvVideo.setImageResource(resID);
        if (isMuted) {
            closeCameraSettings();
            mCameraSettings.setVisibility(View.GONE);
        } else {
            mCameraSettings.setVisibility(View.VISIBLE);
        }
    }
    
    private void closeCameraSettings() {
        mZoomScaleFactor = 1;
        if (mCameraSettingsDialog != null && mCameraSettingsDialog.isShowing()) {
            mCameraSettingsDialog.dismiss();
        }
    }

    private void configurePortraitView() {
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) mSelfView.getLayoutParams();
        params.startToStart = R.id.parent_layout;
        params.topToTop = R.id.parent_layout;
        params.endToEnd = R.id.parent_layout;
        params.dimensionRatio = getResources().getString(R.string.self_view_ratio);
        mSelfView.requestLayout();
    }

    private void configureLandscapeView() {
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) mSelfView.getLayoutParams();
        params.startToStart = ConstraintLayout.LayoutParams.UNSET;
        params.topToTop = R.id.parent_layout;
        params.endToEnd = R.id.parent_layout;
        params.dimensionRatio = getResources().getString(R.string.self_view_ratio);
        mSelfView.requestLayout();
    }

    private void handleRemoteContentState() {
        if (mViewPager.getVisibility() == View.VISIBLE) {
            if (mIsRemoteContentAvailable) {
                hideProgress();
            } else {
                showProgress("No one is sharing the remote content.");
            }
        }
    }

    private void handleVideoState() {
        if (mViewPager.getVisibility() == View.VISIBLE) {
            if (mVideoState instanceof MeetingService.VideoState.Active) {
                hideProgress();
            } else if (mVideoState instanceof MeetingService.VideoState.Inactive.SingleParticipant) {
                showProgress("You are the only participant. Please wait some one to join.");
            } else if (mVideoState instanceof MeetingService.VideoState.Inactive.NoOneHasVideo) {
                Log.i(TAG, "No one is sharing their video");
            } else if (mVideoState instanceof MeetingService.VideoState.Inactive.NeedsModerator) {
                showProgress("Need moderator");
            } else {
                hideProgress();
            }
        }
    }

    private class PagerChangeCallback extends ViewPager2.OnPageChangeCallback {
        @Override
        public void onPageSelected(int position) {
            super.onPageSelected(position);
            if (position == 0) {
                handleVideoState();
            } else if (position == 1) {
                handleRemoteContentState();
            }
        }
    }

    private final IMenuCallback mIOptionMenuCallback =
            new IMenuCallback() {
                @Override
                public void showVideoLayoutView(String videoLayoutName) {
                    updateCurrentVideoLayoutForAlertDialog(videoLayoutName);
                    showVideoLayoutDialog();
                }

                @Override
                public void showAudioDeviceView() {
                    showAudioDeviceDialog();
                }

                @Override
                public void showVideoDeviceView() {
                    showVideoDeviceDialog();
                }

                @Override
                public void handleClosedCaptionSwitchEvent(Boolean enabled) {
                    if (enabled) {
                        mMeetingService.getClosedCaptioningService().startClosedCaptioning();
                        mTvClosedCaption.setVisibility(View.VISIBLE);
                    } else {
                        mMeetingService.getClosedCaptioningService().stopClosedCaptioning();
                        mTvClosedCaption.setVisibility(View.GONE);
                    }
                }

                @Override
                public void showWaitingRoom() {
                    showWaitingRoomDialog();
                }

                @Override
                public void setWaitingRoomEnabled(boolean enabled) {
                    Log.i(TAG, "WR status: " + enabled);
                    mMeetingService.getModeratorWaitingRoomService().setWaitingRoomEnabled(enabled);
                }
            };

    private ArrayList<String> videoLayoutOptionList() {
        ArrayList<String> videoLayoutList = new ArrayList<>();
        videoLayoutList.add(getString(R.string.people_view));
        videoLayoutList.add(getString(R.string.speaker_view));
        videoLayoutList.add(getString(R.string.gallery_view));
        return videoLayoutList;
    }

    private void showVideoLayoutDialog() {
        mVideoLayoutDialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.video_layouts))
                .setAdapter(mVideoLayoutAdapter,
                        (dialog, which) -> selectVideoLayout(which)).create();
        mVideoLayoutDialog.show();
    }

    private void showAudioDeviceDialog() {
        mAudioDialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.audio_devices))
                .setAdapter(mAudioDeviceAdapter,
                        (dialog, which) -> selectAudioDevice(which)).create();
        mAudioDialog.show();
    }

    private void showVideoDeviceDialog() {
        mVideoDeviceDialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.video_devices))
                .setAdapter(mVideoDeviceAdapter,
                        (dialog, which) -> selectVideoDevice(which)).create();
        mVideoDeviceDialog.show();
    }

    private void showWaitingRoomDialog() {
        List<ParticipantsService.Participant> waitingRoomParticipants = mMeetingService.getModeratorWaitingRoomService().getWaitingRoomParticipants();
        if (waitingRoomParticipants != null && waitingRoomParticipants.size() > 0) {
            WaitingRoomDialog.newInstance(waitingRoomParticipants, mMeetingService)
                    .show(getSupportFragmentManager(), "WaitingRoomDialog");
        } else {
            showToastMessage("There are no participants in the waiting room");
        }
    }

    private void updateCurrentVideoDeviceForAlertDialog(VideoDevice videoDevice) {
        List<VideoDevice> videoDevices = mVideoDeviceService.getVideoDevices().getValue();
        if (videoDevices != null) {
            mVideoDeviceAdapter.updateSelectedPosition(videoDevices.indexOf(videoDevice));
        }
    }

    private void updateCurrentAudioDeviceForAlertDialog(AudioDevice currentAudioDevice) {
        List<AudioDevice> audioDevices = mMeetingService.getAudioDeviceService().getAudioDevices().getValue();
        if (audioDevices != null) {
            mAudioDeviceAdapter.updateSelectedPosition(audioDevices.indexOf(currentAudioDevice));
        }
    }

    private void updateCurrentVideoLayoutForAlertDialog(String videoLayoutName) {
        mVideoLayoutAdapter.updateSelectedPosition(videoLayoutOptionList().indexOf(videoLayoutName));
    }

    private VideoLayoutAdapter getVideoLayoutAdapter() {
        return new VideoLayoutAdapter(this, android.R.layout.simple_list_item_single_choice,
                videoLayoutOptionList());
    }

    private VideoDeviceAdapter getVideoDeviceAdapter(List<VideoDevice> videoDevices) {
        return new VideoDeviceAdapter(this, android.R.layout.simple_list_item_single_choice,
                videoDevices);
    }

    private AudioDeviceAdapter getAudioDeviceAdapter(List<AudioDevice> audioDevices) {
        return new AudioDeviceAdapter(this, android.R.layout.simple_list_item_single_choice,
                audioDevices);
    }

    private void setVideoLayout(String videoLayoutName) {
        MeetingService.VideoLayout videoLayout = null;
        if (videoLayoutName.equals(getString(R.string.people_view))) {
            videoLayout = MeetingService.VideoLayout.People.INSTANCE;
        } else if (videoLayoutName.equals(getString(R.string.gallery_view))) {
            videoLayout = MeetingService.VideoLayout.Gallery.INSTANCE;
        } else if (videoLayoutName.equals(getString(R.string.speaker_view))) {
            videoLayout = MeetingService.VideoLayout.Speaker.INSTANCE;
        }
        if (videoLayout != null) {
            mMeetingService.setVideoLayout(videoLayout);
        }
    }

    private void selectAudioDevice(int position) {
        AudioDevice audioDevice = mAudioDeviceAdapter.getItem(position);
        mAudioDeviceAdapter.updateSelectedPosition(position);
        mMeetingService.getAudioDeviceService().selectAudioDevice(audioDevice);
    }

    private void selectVideoDevice(int position) {
        mVideoDeviceAdapter.updateSelectedPosition(position);
        VideoDevice videoDevice = mVideoDeviceAdapter.getItem(position);
        closeCameraSettings();
        mVideoDeviceService.selectVideoDevice(videoDevice);
    }

    private void selectVideoLayout(int position) {
        mVideoLayoutAdapter.updateSelectedPosition(position);
        setVideoLayout(mVideoLayoutAdapter.getItem(position));
    }

    public void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void showCameraSettingsDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final SeekBar seek = new SeekBar(this);
        seek.setMax(10);
        seek.setMin(1);
        seek.setProgress(mZoomScaleFactor);
        builder.setTitle(getString(R.string.camera_setting_title));
        builder.setView(seek);
        try {
            CameraCharacteristics cameraCharacteristics = getCurrentCameraCharacteristics();
            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    mZoomScaleFactor = progress;
                    Rect activeRegion = cameraCharacteristics
                            .get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                    mVideoDeviceService.setRepeatingCaptureRequest(CaptureRequest.SCALER_CROP_REGION,
                            getCropRegionForZoom(activeRegion, progress), null);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        mCameraSettingsDialog = builder.create();
        mCameraSettingsDialog.show();
    }

    private void handleMeetingState(MeetingService.MeetingState meetingState) {
        if (meetingState instanceof MeetingService.MeetingState.Connected) {
            // add this flag to avoid screen shots.
            // This also allows protection of screen during screen casts from 3rd party apps.
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            OnGoingMeetingService.startService(getApplicationContext());
            subscribeForModeratorWaitingRoomEvents();
            isSubscribeToWaitingRoomEvents = true;
        } else if (meetingState instanceof MeetingService.MeetingState.Idle) {
            endMeeting();
            isInWaitingRoom = false;
            showWaitingRoomUI();
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else if (meetingState instanceof  MeetingService.MeetingState.WaitingRoom) {
            isInWaitingRoom = true;
            showOutOfMeetingView();
            showWaitingRoomUI();
        } else if (meetingState instanceof MeetingService.MeetingState.Connecting) {
            mMeetingService.setAudioMuted(mIsAudioMuted);
            mMeetingService.setVideoMuted(mIsVideoMuted);
            mVideoDeviceService.enableSelfVideoPreview(false);
            isInWaitingRoom = false;
            showWaitingRoomUI();
            showInMeetingView();
            mEtPassCode.setVisibility(View.GONE);
            mEtName.setVisibility(View.GONE);
            mEtEventId.setVisibility(View.GONE);
            btnJoin.setVisibility(View.GONE);
            mJoinLayout.setVisibility(View.GONE);
            mTvProgressMsg.setVisibility(View.VISIBLE);
            mTvProgressMsg.setText("Connecting...");
        }
    }

    /**
     * It calculates the new crop region by finding out the delta between active camera region's
     * x and y coordinates and divide by zoom scale factor to get updated camera's region.
     *
     * @param cameraActiveRegion active area of the image sensor.
     * @param zoomFactor scale factor
     * @return Rect coordinates of crop region to be zoomed.
     */
    private Rect getCropRegionForZoom(Rect cameraActiveRegion, int zoomFactor) {
        int xCenter = cameraActiveRegion.width() / 2;
        int yCenter = cameraActiveRegion.height() / 2;
        int xDelta = (int) (0.5f * cameraActiveRegion.width() / zoomFactor);
        int yDelta = (int) (0.5f * cameraActiveRegion.height() / zoomFactor);
        return new Rect(xCenter - xDelta, yCenter - yDelta, xCenter + xDelta,
                yCenter + yDelta);
    }

    private CameraCharacteristics getCurrentCameraCharacteristics() throws CameraAccessException {
        VideoDevice currentVideoDevice = mVideoDeviceService.getCurrentVideoDevice().getValue();
        if (currentVideoDevice != null) {
            String cameraId = mVideoDeviceService.getCurrentVideoDevice().getValue().getId();
            CameraManager cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
            return cameraManager.getCameraCharacteristics(cameraId);
        } else {
            Log.e(TAG, "No active camera device");
            throw new CameraAccessException(CameraAccessException.CAMERA_ERROR);
        }
    }

    private void showWaitingRoomUI() {
        if (isInWaitingRoom) {
            mEtPassCode.setVisibility(View.GONE);
            mEtName.setVisibility(View.GONE);
            mEtEventId.setVisibility(View.GONE);
            btnJoin.setVisibility(View.GONE);
            mJoinLayout.setVisibility(View.GONE);
            mWaitingRoomLayout.setVisibility(View.VISIBLE);
        } else {
            mEtPassCode.setVisibility(View.VISIBLE);
            mEtName.setVisibility(View.VISIBLE);
            mEtEventId.setVisibility(View.VISIBLE);
            btnJoin.setVisibility(View.VISIBLE);
            mJoinLayout.setVisibility(View.VISIBLE);
            mWaitingRoomLayout.setVisibility(View.GONE);
        }
        hideProgress();
    }
}