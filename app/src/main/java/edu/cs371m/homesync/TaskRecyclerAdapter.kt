package edu.cs371m.homesync

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TaskRecyclerAdapter(
    private val tasks: List<Task>
) : RecyclerView.Adapter<TaskRecyclerAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameTextView: TextView = view.findViewById(R.id.taskName)
        val statusTextView: TextView = view.findViewById(R.id.taskStatus)
        val assigneeTextView: TextView = view.findViewById(R.id.taskAssignee)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val task = tasks[position]
        holder.nameTextView.text = task.name
        holder.statusTextView.text = task.status
        holder.assigneeTextView.text = "Assigned to: ${task.assignee}"
    }

    override fun getItemCount(): Int = tasks.size
}