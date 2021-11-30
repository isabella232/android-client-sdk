/*
 * Copyright (c) 2021 Blue Jeans Network, Inc. All rights reserved.
 */
package com.bluejeans.android.sdksample.participantlist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bluejeans.android.sdksample.SampleApplication
import com.bluejeans.android.sdksample.databinding.LayoutParticipantItemBinding
import com.bluejeans.android.sdksample.participantlist.ParticipantListAdapter.RosterViewHolder
import com.bluejeans.bluejeanssdk.BlueJeansSDK
import com.bluejeans.bluejeanssdk.meeting.MeetingService
import com.bluejeans.bluejeanssdk.meeting.ParticipantsService

class ParticipantListAdapter constructor(
    private val isForChat: Boolean = false,
    private val chatItemListener: ParticipantChatItemListener? = null
) : RecyclerView.Adapter<RosterViewHolder>() {
    private val participantList = ArrayList<ParticipantsService.Participant>()
    private var everyone = ParticipantsService.Participant(EVERYONE)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RosterViewHolder {
        val itemViewBinding = LayoutParticipantItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent, false
        )
        return RosterViewHolder(itemViewBinding)
    }

    override fun onBindViewHolder(holder: RosterViewHolder, position: Int) {
        holder.bind(this.participantList[position])
    }

    override fun getItemCount(): Int {
        return this.participantList.size
    }

    fun updateMeetingList(participantsList: List<ParticipantsService.Participant>) {
        participantList.clear()
        if (isForChat && !participantsList.contains(everyone)) {
            this.participantList.add(everyone)
        }
        this.participantList.addAll(participantsList)
        /*
          If you are the only participant in the meeting, will not be shown in the people
          chat list.By default 'Every one' is added in the chat list which is basically
          public chat list item.
         */
        if (isForChat && participantList.size >= 2) {
            // remove self participant
            participantList.removeAt(1)
        }
        notifyDataSetChanged()
    }

    inner class RosterViewHolder(private val bindingView: LayoutParticipantItemBinding) :
        RecyclerView.ViewHolder(bindingView.root) {
        fun bind(participant: ParticipantsService.Participant) {
            if (participant.id == EVERYONE) {
                bindingView.tvParticipantName.text = EVERYONE
            } else {
                bindingView.tvParticipantName.text = participant.name
            }
            bindingView.ivRosterAudioStatus.isSelected = participant.isAudioMuted
            bindingView.ivRosterVideoStatus.isSelected = participant.isVideoMuted
            if (isForChat) {
                bindingView.ivRosterAudioStatus.visibility = View.GONE
                bindingView.ivRosterVideoStatus.visibility = View.GONE
                bindingView.ivPrivateChat.visibility = View.VISIBLE
                chatItemListener?.onMessageCountChange(participant, bindingView.tvUnreadCount)
                bindingView.ivPrivateChat.setOnClickListener {
                    chatItemListener?.onParticipantClick(
                        participant
                    )
                }
            } else {
                bindingView.ivRosterAudioStatus.visibility = View.VISIBLE
                bindingView.ivRosterVideoStatus.visibility = View.VISIBLE
                bindingView.ivPrivateChat.visibility = View.GONE
            }

            if (!participant.isSelf && !isForChat && SampleApplication.blueJeansSDK.
                meetingService.moderatorControlsService.isModeratorControlsAvailable.value == true && SampleApplication.blueJeansSDK.
                meetingService.moderatorWaitingRoomService.isWaitingRoomEnabled.value == true) {
                bindingView.btnDemote.setOnClickListener {
                    SampleApplication.blueJeansSDK.meetingService.moderatorWaitingRoomService.demote(
                        participant
                    )
                }
            } else {
                bindingView.btnDemote.visibility = View.GONE
            }
        }
    }
}