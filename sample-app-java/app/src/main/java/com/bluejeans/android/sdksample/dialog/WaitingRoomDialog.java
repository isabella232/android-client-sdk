package com.bluejeans.android.sdksample.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.RecyclerView;
import com.bluejeans.android.sdksample.R;
import com.bluejeans.android.sdksample.participantlist.WaitingRoomParticipantListAdapter;
import com.bluejeans.bluejeanssdk.meeting.MeetingService;
import com.bluejeans.bluejeanssdk.meeting.ParticipantsService;
import java.util.List;

public class WaitingRoomDialog extends DialogFragment {
    private List<ParticipantsService.Participant> paticipants;
    private MeetingService meetingService;

    private RecyclerView recyclerView;
    private Button btnAdmitAll, btnDenyAll;

    private WaitingRoomParticipantListAdapter waitingRoomPartitipantListAdapter;

    private static WaitingRoomDialog waitingRoomDialog;
    private static final String TAG = "WaitingRoomDialog";

    private final WaitingRoomParticipantListAdapter.IWaitingRoomActionsCallback mWaitingRoomActionCallback = new WaitingRoomParticipantListAdapter.IWaitingRoomActionsCallback() {
        @Override
        public void admitParticipant(ParticipantsService.Participant participant) {
            meetingService.getModeratorWaitingRoomService().admitParticipant(participant);
            dismiss();
        }

        @Override
        public void denyParticipant(ParticipantsService.Participant participant) {
            meetingService.getModeratorWaitingRoomService().denyParticipant(participant);
            dismiss();
        }
    };

    private WaitingRoomDialog(List<ParticipantsService.Participant> paticipants, MeetingService meetingService) {
        this.paticipants = paticipants;
        this.meetingService = meetingService;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_waiting_room_dialog, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerView = view.findViewById(R.id.rv_waiting_room_participants);
        btnAdmitAll = view.findViewById(R.id.btn_admit_all);
        btnDenyAll = view.findViewById(R.id.btn_deny_all);

        waitingRoomPartitipantListAdapter = new WaitingRoomParticipantListAdapter(paticipants, mWaitingRoomActionCallback);
        recyclerView.setAdapter(waitingRoomPartitipantListAdapter);

        btnAdmitAll.setOnClickListener(view1 -> {
            meetingService.getModeratorWaitingRoomService().admitAll();
            dismiss();
        });

        btnDenyAll.setOnClickListener(view1 -> {
            meetingService.getModeratorWaitingRoomService().denyAll();
            dismiss();
        });
    }

    public static WaitingRoomDialog newInstance(List<ParticipantsService.Participant> participants,
                                                MeetingService meetingService) {
        waitingRoomDialog = new WaitingRoomDialog(participants, meetingService);

        return waitingRoomDialog;
    }
}
