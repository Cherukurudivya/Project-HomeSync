package edu.cs371m.homesync

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HistoryRecyclerAdapter(
    private val schedules: List<Schedule>
) : RecyclerView.Adapter<HistoryRecyclerAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val activityTextView: TextView = view.findViewById(R.id.scheduleActivity)
        val timeTextView: TextView = view.findViewById(R.id.scheduleTime)
        val assigneeTextView: TextView = view.findViewById(R.id.scheduleAssignee)
        val deleteButton: Button = view.findViewById(R.id.deleteScheduleButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schedule, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val schedule = schedules[position]
        holder.activityTextView.text = schedule.activity
        holder.timeTextView.text = "${schedule.date} ${schedule.time}"
        holder.assigneeTextView.text = "Assigned to: ${schedule.assignee}"
        holder.deleteButton.visibility = View.GONE
    }

    override fun getItemCount(): Int = schedules.size
}