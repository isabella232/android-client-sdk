/**
 * Copyright (c) 2021 Blue Jeans Network, Inc. All rights reserved.
 */
package com.bluejeans.android.sdksample.participantlist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bluejeans.android.sdksample.R
import com.bluejeans.bluejeanssdk.meeting.ParticipantsService

class WaitingRoomParticipantListAdapter(
    private val participants: List<ParticipantsService.Participant>,
    private val waitingRoomActionCallback: IWaitingRoomActionsCallback
) : RecyclerView.Adapter<WaitingRoomParticipantListAdapter.ViewHolder>() {

    interface IWaitingRoomActionsCallback {
        fun admitParticipant(participant: ParticipantsService.Participant)
        fun denyParticipant(participant: ParticipantsService.Participant)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvWaitingRoomParticipantName: TextView = view.findViewById(R.id.tv_waiting_room_participant_name)
        val btnDeny: Button = view.findViewById(R.id.btn_deny)
        val btnAdmit: Button = view.findViewById(R.id.btn_admit)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.waiting_room_dialog_row, parent, false)

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val participant = participants.elementAt(position)
        holder.tvWaitingRoomParticipantName.text = participant.name

        holder.btnAdmit.setOnClickListener {
            waitingRoomActionCallback.admitParticipant(participant)
        }

        holder.btnDeny.setOnClickListener {
            waitingRoomActionCallback.denyParticipant(participant)
        }
    }

    override fun getItemCount(): Int {
        return participants.size
    }
}
