/*
 * Copyright (c) 2021 Blue Jeans Network, Inc. All rights reserved.
 */
package com.bluejeans.android.sdksample.menu;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import com.bluejeans.android.sdksample.R;
import com.bluejeans.android.sdksample.SampleApplication;
import com.bluejeans.rxextensions.ObservableValueWithOptional;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import io.reactivex.rxjava3.disposables.Disposable;
import kotlin.Unit;

public class MenuFragment extends BottomSheetDialogFragment {
    private static final String TAG = "MenuFragment";

    private IMenuCallback mIMenuCallback;
    private boolean mIsWaitingRoomEnabled;
    private MaterialButton mMbVideoLayout, mMbAudioDevice, mMbVideoDevice;
    private Button mViewWaitingRoom;
    private String mVideoLayout = "";
    private String mCurrentAudioDevice = "";
    private String mCurrentVideoDevice = "";
    private boolean mClosedCaptionState =  false;
    private SwitchCompat mSwitchClosedCaption, mSwitchWaitingRoom;
    private LinearLayout mWaitingRoomLayout;

    private Disposable mWaitingRoomEnablementDisposable;

    public interface IMenuCallback {
        void showVideoLayoutView(String videoLayoutName);

        void showAudioDeviceView();

        void showVideoDeviceView();

        void handleClosedCaptionSwitchEvent(Boolean enabled);

        void showWaitingRoom();

        void setWaitingRoomEnabled(boolean enabled);
    }

    public MenuFragment(IMenuCallback iMenuCallback, boolean isWaitingRoomEnabled) {
        mIMenuCallback = iMenuCallback;
        mIsWaitingRoomEnabled = isWaitingRoomEnabled;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_option_menu_dialog, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();
        BottomSheetBehavior<View> bottomSheetBehavior = BottomSheetBehavior.from((View) requireView().getParent());
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mWaitingRoomEnablementDisposable != null && !mWaitingRoomEnablementDisposable.isDisposed())
            mWaitingRoomEnablementDisposable.dispose();
    }

    public void updateVideoLayout(String videoLayout) {
        this.mVideoLayout = videoLayout;
        updateView();
    }

    public void updateAudioDevice(String currentAudioDevice) {
        this.mCurrentAudioDevice = currentAudioDevice;
        updateView();
    }

    public void updateVideoDevice(String currentVideoDevice) {
        this.mCurrentVideoDevice = currentVideoDevice;
        updateView();
    }

    public void updateClosedCaptionSwitchState(boolean isClosedCaptionActive) {
        mClosedCaptionState = isClosedCaptionActive;
    }

    private void initViews(View view) {
        mMbVideoLayout = view.findViewById(R.id.mbVideoLayout);
        mMbAudioDevice = view.findViewById(R.id.mbAudioDevice);
        mMbVideoDevice = view.findViewById(R.id.mbVideoDevice);
        mSwitchClosedCaption = view.findViewById(R.id.swClosedCaption);

        if (SampleApplication.getBlueJeansSDK().getBlueJeansClient().getMeetingSession().isModerator()) {
            mWaitingRoomLayout = view.findViewById(R.id.llWaitingRoom);
            mWaitingRoomLayout.setVisibility(View.VISIBLE);

            mViewWaitingRoom = view.findViewById(R.id.btnShowWaitingRoom);
            mSwitchWaitingRoom = view.findViewById(R.id.swWaitingRoom);

            boolean isWaitingRoomCapable = SampleApplication.getBlueJeansSDK().getMeetingService().getModeratorWaitingRoomService().isWaitingRoomCapable().getValue();
            if (isWaitingRoomCapable == true) {

                mViewWaitingRoom.setOnClickListener(view1 -> {
                    mIMenuCallback.showWaitingRoom();
                    dismiss();
                });

                mWaitingRoomEnablementDisposable = SampleApplication.getBlueJeansSDK().getMeetingService().getModeratorWaitingRoomService().isWaitingRoomEnabled()
                        .subscribe(
                                SampleApplication.getBlueJeansSDK().getBlueJeansClient().bjnScheduler.applyUIScheduler(),
                                isEnabled -> {
                                    if (isEnabled) {
                                        mSwitchWaitingRoom.setChecked(true);
                                    } else {
                                        mSwitchWaitingRoom.setChecked(false);
                                    }
                                    return Unit.INSTANCE;
                                }, err -> {
                                    Log.e(TAG, "Error occured while getting isWaitingRoomEnabled value");
                                    return Unit.INSTANCE;
                                }
                        );

                mSwitchWaitingRoom.setOnCheckedChangeListener((buttonView, isChecked) -> mIMenuCallback.setWaitingRoomEnabled(isChecked));
            } else if (isWaitingRoomCapable == false) {
                mViewWaitingRoom.setEnabled(false);
                mSwitchWaitingRoom.setEnabled(false);
            }
        }

        mMbVideoLayout.setOnClickListener(view1 -> {
            mIMenuCallback.showVideoLayoutView(mMbVideoLayout.getText().toString());
            dismiss();
        });

        mMbAudioDevice.setOnClickListener(view1 -> {
            mIMenuCallback.showAudioDeviceView();
            dismiss();
        });
        mMbVideoDevice.setOnClickListener(view1 -> {
            mIMenuCallback.showVideoDeviceView();
            dismiss();
        });

        if (this.mIsWaitingRoomEnabled) {
            mSwitchWaitingRoom.setChecked(this.mIsWaitingRoomEnabled);
        }

        ObservableValueWithOptional<Boolean> closedCaptionFeatureObservable = SampleApplication.getBlueJeansSDK().getMeetingService()
                .getClosedCaptioningService().isClosedCaptioningAvailable();
        if (closedCaptionFeatureObservable.getValue() == Boolean.TRUE) {
            mSwitchClosedCaption.setChecked(mClosedCaptionState);
            mSwitchClosedCaption.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (buttonView.isPressed()) {
                    mIMenuCallback.handleClosedCaptionSwitchEvent(isChecked);
                    mClosedCaptionState = isChecked;
                    dismiss();
                }
            });
            mSwitchClosedCaption.setVisibility(View.VISIBLE);
        } else {
            mSwitchClosedCaption.setVisibility(View.GONE);
        }

        updateView();
    }

    private void updateView() {
        if (mMbVideoLayout != null) {
            mMbVideoLayout.setText(mVideoLayout);
        }
        if (mMbAudioDevice != null) {
            mMbAudioDevice.setText(mCurrentAudioDevice);
        }
        if (mMbVideoDevice != null) {
            mMbVideoDevice.setText(mCurrentVideoDevice);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mSwitchClosedCaption.setChecked(mClosedCaptionState);
    }
}
