/**
 * Copyright (c) 2021 Blue Jeans Network, Inc. All rights reserved.
 */
package com.bluejeans.android.sdksample.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import com.bluejeans.android.sdksample.R
import com.bluejeans.android.sdksample.participantlist.WaitingRoomParticipantListAdapter
import com.bluejeans.bluejeanssdk.meeting.MeetingService
import com.bluejeans.bluejeanssdk.meeting.ParticipantsService

class WaitingRoomDialog(
    private val participants: List<ParticipantsService.Participant>,
    private val meetingService: MeetingService) : DialogFragment() {

    private lateinit var waitingRoomPartitipantListAdapter: WaitingRoomParticipantListAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnAdmitAll: Button
    private lateinit var btnDenyAll: Button

    private val mWaitingRoomActionCallback = object : WaitingRoomParticipantListAdapter.IWaitingRoomActionsCallback {
        override fun admitParticipant(participant: ParticipantsService.Participant) {
            meetingService.moderatorWaitingRoomService.admitParticipant(participant)
            dismiss()
        }

        override fun denyParticipant(participant: ParticipantsService.Participant) {
            meetingService.moderatorWaitingRoomService.denyParticipant(participant)
            dismiss()
        }

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_waiting_room_dialog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.rv_waiting_room_participants)
        btnAdmitAll = view.findViewById(R.id.btn_admit_all)
        btnDenyAll = view.findViewById(R.id.btn_deny_all)

        waitingRoomPartitipantListAdapter = WaitingRoomParticipantListAdapter(participants, mWaitingRoomActionCallback)
        recyclerView.adapter = waitingRoomPartitipantListAdapter

        btnAdmitAll.setOnClickListener {
            meetingService.moderatorWaitingRoomService.admitAll()
            dismiss()
        }

        btnDenyAll.setOnClickListener {
            meetingService.moderatorWaitingRoomService.denyAll()
            dismiss()
        }
    }

    companion object {
        const val TAG = "WaitingRoomDialog"
        fun newInstance(participants: List<ParticipantsService.Participant>,
                        meetingService: MeetingService) = WaitingRoomDialog(participants, meetingService)
    }

}
