package edu.cs371m.homesync

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.AdapterView
import com.google.firebase.auth.FirebaseAuth

class TaskAdapter(
    context: Context,
    private val tasks: List<Task>,
    private val userRole: String,
    private val familyId: String,
    private val currentUserEmail: String,
    private val onTaskAction: (String, TaskAction) -> Unit
) : ArrayAdapter<Task>(context, 0, tasks) {

    private val auth = FirebaseAuth.getInstance()
    private val statuses = listOf("Assigned", "In Progress", "Completed")

    sealed class TaskAction {
        data class UpdateStatus(val status: String) : TaskAction()
        object Delete : TaskAction()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.task_row, parent, false)
        val task = tasks[position]

        val taskNameTextView = view.findViewById<TextView>(R.id.taskNameTextView)
        val taskAssigneeTextView = view.findViewById<TextView>(R.id.taskAssigneeTextView)
        taskNameTextView.text = task.name
        taskAssigneeTextView.text = "Assigned to: ${task.assignee}"

        val statusSpinner = view.findViewById<Spinner>(R.id.taskStatusSpinner)
        val statusAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, statuses)
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        statusSpinner.adapter = statusAdapter

        val currentStatusIndex = statuses.indexOf(task.status)
        statusSpinner.setSelection(if (currentStatusIndex != -1) currentStatusIndex else 0)

        val deleteButton = view.findViewById<Button>(R.id.deleteTaskButton)
        deleteButton.visibility = if (userRole == "parent") View.VISIBLE else View.GONE
        deleteButton.setOnClickListener {
            onTaskAction(task.id, TaskAction.Delete)
        }

        statusSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                val selectedStatus = statuses[pos]
                if (selectedStatus != task.status) {
                    if (userRole == "kid" && task.assignee != currentUserEmail) {
                        statusSpinner.setSelection(statuses.indexOf(task.status))
                        onTaskAction("", TaskAction.UpdateStatus("You can only update your own tasks"))
                        return
                    }
                    onTaskAction(task.id, TaskAction.UpdateStatus(selectedStatus))
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
            }
        }

        return view
    }
}