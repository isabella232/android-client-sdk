package com.bluejeans.android.sdksample.participantlist;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bluejeans.android.sdksample.R;
import com.bluejeans.bluejeanssdk.meeting.ParticipantsService;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class WaitingRoomParticipantListAdapter extends
        RecyclerView.Adapter<WaitingRoomParticipantListAdapter.ViewHolder> {

    private List<ParticipantsService.Participant> participants;
    private IWaitingRoomActionsCallback waitingRoomActionsCallback;

    public WaitingRoomParticipantListAdapter(List<ParticipantsService.Participant> participants,
                                             IWaitingRoomActionsCallback waitingRoomActionsCallback) {
        this.participants = participants;
        this.waitingRoomActionsCallback = waitingRoomActionsCallback;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.waiting_room_dialog_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ParticipantsService.Participant participant = participants.get(position);

        holder.tvWaitingRoomParticipantName.setText(participant.getName());

        holder.btnAdmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                waitingRoomActionsCallback.admitParticipant(participant);
            }
        });

        holder.btnDeny.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                waitingRoomActionsCallback.denyParticipant(participant);
            }
        });
    }

    @Override
    public int getItemCount() {
        return this.participants.size();
    }

    public interface IWaitingRoomActionsCallback {
        void admitParticipant(ParticipantsService.Participant participant);
        void denyParticipant(ParticipantsService.Participant participant);
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        @NotNull
        private final TextView tvWaitingRoomParticipantName;
        @NotNull
        private final Button btnDeny;
        @NotNull
        private final Button btnAdmit;
        
        public ViewHolder(@NotNull View view) {
            super(view);
            tvWaitingRoomParticipantName = view.findViewById(R.id.tv_waiting_room_participant_name);
            btnDeny = view.findViewById(R.id.btn_deny);
            btnAdmit = view.findViewById(R.id.btn_admit);
        }
    }
}
