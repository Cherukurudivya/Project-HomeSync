package edu.cs371m.homesync

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import edu.cs371m.homesync.databinding.ActivityTaskBinding

class TaskActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTaskBinding
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private val users = mutableListOf<String>()
    private val userEmails = mutableMapOf<String, String>() // Maps display name to email
    private val tasks = mutableListOf<Task>()
    private lateinit var taskAdapter: TaskAdapter
    private var userRole: String? = null
    private var familyId: String? = null
    private var currentUserEmail: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTaskBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference
        currentUserEmail = auth.currentUser?.email

        familyId = intent.getStringExtra("familyId")
        if (familyId == null) {
            Toast.makeText(this, "Family ID not found", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        determineUserRole()

        // Setup assignee spinner
        val assigneeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, users)
        assigneeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.assigneeSpinner.adapter = assigneeAdapter

        taskAdapter = TaskAdapter(this, tasks, userRole ?: "kid", familyId!!, currentUserEmail ?: "") { taskId, action ->
            when (action) {
                is TaskAdapter.TaskAction.UpdateStatus -> updateTaskStatus(taskId, action.status)
                is TaskAdapter.TaskAction.Delete -> deleteTask(taskId)
            }
        }
        binding.taskListView.adapter = taskAdapter

        fetchFamilyMembers()
        fetchTasks()

        binding.saveTaskButton.setOnClickListener {
            if (userRole == "parent") {
                val name = binding.taskNameEditText.text.toString()
                val assigneeDisplayName = binding.assigneeSpinner.selectedItem?.toString() ?: ""
                val assigneeEmail = userEmails[assigneeDisplayName] ?: assigneeDisplayName
                if (name.isNotEmpty() && assigneeDisplayName.isNotEmpty()) {
                    saveTask(name, assigneeEmail)
                } else {
                    Toast.makeText(this, "Please enter task name and select assignee", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Only parents can create tasks", Toast.LENGTH_SHORT).show()
            }
        }

        binding.goToSchedulesButton.setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java).apply {
                putExtra("familyId", familyId)
            }
            startActivity(intent)
            finish()
        }
    }

    private fun determineUserRole() {
        val userId = auth.currentUser?.uid ?: return
        database.child("families").child(familyId!!).child("users").child(userId).child("role")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    userRole = snapshot.getValue(String::class.java) ?: "kid"
                    Log.d("HomeSync", "User role determined: $userRole")
                    if (userRole == "kid") {
                        binding.taskNameEditText.visibility = View.GONE
                        binding.assigneeSpinner.visibility = View.GONE
                        binding.saveTaskButton.visibility = View.GONE
                    }
                    taskAdapter = TaskAdapter(this@TaskActivity, tasks, userRole!!, familyId!!, currentUserEmail ?: "") { taskId, action ->
                        when (action) {
                            is TaskAdapter.TaskAction.UpdateStatus -> updateTaskStatus(taskId, action.status)
                            is TaskAdapter.TaskAction.Delete -> deleteTask(taskId)
                        }
                    }
                    binding.taskListView.adapter = taskAdapter
                    fetchTasks()
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@TaskActivity, "Error fetching user role: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun fetchFamilyMembers() {
        val familyId = familyId ?: return
        database.child("families").child(familyId).child("users")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    users.clear()
                    userEmails.clear()
                    for (userSnapshot in snapshot.children) {
                        val email = userSnapshot.child("email").getValue(String::class.java)
                        val displayName = userSnapshot.child("displayName").getValue(String::class.java)
                            ?: email
                        if (displayName != null) {
                            users.add(displayName)
                            if (email != null) {
                                userEmails[displayName] = email
                            }
                        }
                    }
                    (binding.assigneeSpinner.adapter as ArrayAdapter<*>).notifyDataSetChanged()
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@TaskActivity, "Error fetching users: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun fetchTasks() {
        val familyId = familyId ?: return
        database.child("families").child(familyId).child("tasks")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    tasks.clear()
                    for (taskSnapshot in snapshot.children) {
                        val id = taskSnapshot.key ?: continue
                        val name = taskSnapshot.child("name").getValue(String::class.java) ?: ""
                        val assigneeEmail = taskSnapshot.child("assignee").getValue(String::class.java) ?: ""
                        val status = taskSnapshot.child("status").getValue(String::class.java) ?: "Assigned"
                        val assigneeDisplayName = users.find { userEmails[it] == assigneeEmail } ?: assigneeEmail
                        val task = Task(id, name, assigneeDisplayName, status)
                        if (userRole == "kid" && assigneeEmail != currentUserEmail) continue
                        tasks.add(task)
                    }
                    taskAdapter.notifyDataSetChanged()
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@TaskActivity, "Error fetching tasks: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun saveTask(name: String, assigneeEmail: String) {
        val familyId = familyId ?: return
        val taskId = database.child("families").child(familyId).child("tasks").push().key ?: return
        val task = mapOf(
            "name" to name,
            "assignee" to assigneeEmail,
            "status" to "Assigned"
        )
        database.child("families").child(familyId).child("tasks").child(taskId)
            .setValue(task)
            .addOnSuccessListener {
                Toast.makeText(this, "Task saved", Toast.LENGTH_SHORT).show()
                binding.taskNameEditText.text.clear()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error saving task", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateTaskStatus(taskId: String, status: String) {
        val familyId = familyId ?: return
        database.child("families").child(familyId).child("tasks").child(taskId).child("status")
            .setValue(status)
            .addOnSuccessListener {
                Toast.makeText(this, "Task marked as $status", Toast.LENGTH_SHORT).show()
                if (status == "Completed") {
                    saveTaskToHistory(taskId)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error updating task", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteTask(taskId: String) {
        if (userRole != "parent") {
            Toast.makeText(this, "Only parents can delete tasks", Toast.LENGTH_SHORT).show()
            return
        }
        val familyId = familyId ?: return
        database.child("families").child(familyId).child("tasks").child(taskId)
            .removeValue()
            .addOnSuccessListener {
                Toast.makeText(this, "Task deleted", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error deleting task", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveTaskToHistory(taskId: String) {
        val task = tasks.find { it.id == taskId } ?: return
        val historyId = database.child("families").child(familyId!!).child("history").push().key ?: return
        val historyEntry = mapOf(
            "taskId" to taskId,
            "name" to task.name,
            "assignee" to task.assignee,
            "completedAt" to System.currentTimeMillis().toString()
        )
        database.child("families").child(familyId!!).child("history").child(historyId)
            .setValue(historyEntry)
            .addOnSuccessListener {
                Log.d("HomeSync", "Task saved to history")
            }
            .addOnFailureListener {
                Log.e("HomeSync", "Error saving task to history")
            }
    }
}