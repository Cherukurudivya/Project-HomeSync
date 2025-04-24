package edu.cs371m.homesync

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import edu.cs371m.homesync.databinding.ActivityDashboardBinding
import java.text.SimpleDateFormat
import java.util.*

class DashboardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDashboardBinding
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private val tasks = mutableListOf<Task>()
    private val schedules = mutableListOf<Schedule>()
    private lateinit var taskAdapter: TaskRecyclerAdapter
    private lateinit var scheduleAdapter: ScheduleRecyclerAdapter
    private var familyId: String? = null
    private var userRole: String? = null
    private val userDisplayNames = mutableMapOf<String, String>()
    private val TAG = "DashboardActivity"

    companion object {
        private const val KEY_FAMILY_ID = "familyId"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        // Restore familyId and userRole from saved state or intent
        familyId = savedInstanceState?.getString(KEY_FAMILY_ID) ?: intent.getStringExtra("familyId")
        userRole = intent.getStringExtra("userRole") ?: "kid"
        Log.d(TAG, "Received familyId: $familyId, userRole: $userRole")

        if (familyId == null) {
            val userId = auth.currentUser?.uid
            if (userId != null) {
                fetchFamilyId(userId)
            } else {
                Toast.makeText(this, "Family ID not found. Please log in again.", Toast.LENGTH_LONG).show()
                redirectToMainActivity()
                return
            }
        } else {
            initializeUI()
        }
    }

    private fun fetchFamilyId(userId: String) {
        binding.loadingProgressBar.visibility = View.VISIBLE
        database.child("users").child(userId).child("familyId")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    familyId = snapshot.getValue(String::class.java)
                    binding.loadingProgressBar.visibility = View.GONE
                    if (familyId == null) {
                        Toast.makeText(this@DashboardActivity, "Family ID not found in database.", Toast.LENGTH_LONG).show()
                        redirectToMainActivity()
                    } else {
                        initializeUI()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    binding.loadingProgressBar.visibility = View.GONE
                    Toast.makeText(this@DashboardActivity, "Error fetching family ID: ${error.message}", Toast.LENGTH_LONG).show()
                    redirectToMainActivity()
                }
            })
    }

    private fun initializeUI() {
        // Initialize adapters
        taskAdapter = TaskRecyclerAdapter(tasks)
        binding.tasksRecyclerView.adapter = taskAdapter
        binding.tasksRecyclerView.layoutManager = LinearLayoutManager(this)

        scheduleAdapter = ScheduleRecyclerAdapter(schedules)
        binding.schedulesRecyclerView.adapter = scheduleAdapter
        binding.schedulesRecyclerView.layoutManager = LinearLayoutManager(this)

        fetchFamilyMembers()
        determineUserRole()
        setWelcomeMessage()
        fetchTasks()
        fetchSchedules()

        binding.goToTasksButton.setOnClickListener {
            val intent = Intent(this, TaskActivity::class.java)
            intent.putExtra("familyId", familyId)
            startActivity(intent)
        }

        binding.goToSchedulesButton.setOnClickListener {
            val intent = Intent(this, ScheduleActivity::class.java)
            intent.putExtra("familyId", familyId)
            startActivity(intent)
        }

        binding.goToHistoryButton.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            intent.putExtra("familyId", familyId)
            startActivity(intent)
        }

        binding.goToProfileButton.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            intent.putExtra("familyId", familyId)
            startActivity(intent)
        }

        binding.refreshButton.setOnClickListener {
            fetchTasks()
            fetchSchedules()
            Toast.makeText(this, "Data refreshed", Toast.LENGTH_SHORT).show()
        }

        binding.logoutButton.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        familyId?.let { outState.putString(KEY_FAMILY_ID, it) }
    }

    private fun redirectToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun determineUserRole() {
        val userId = auth.currentUser?.uid ?: return
        database.child("families").child(familyId!!).child("users").child(userId).child("role")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    userRole = snapshot.getValue(String::class.java) ?: "kid"
                    Log.d(TAG, "Database role for userId $userId: $userRole")
                    if (userRole == "kid") {
                        binding.goToSchedulesButton.visibility = View.GONE
                        Toast.makeText(this@DashboardActivity, "Kid view: Limited access", Toast.LENGTH_SHORT).show()
                    } else {
                        binding.goToSchedulesButton.visibility = View.VISIBLE
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error fetching user role: ${error.message}")
                    Toast.makeText(this@DashboardActivity, "Error fetching user role: ${error.message}", Toast.LENGTH_SHORT).show()
                    binding.goToSchedulesButton.visibility = View.GONE // Fallback to kid view
                }
            })
    }

    private fun setWelcomeMessage() {
        val userId = auth.currentUser?.uid ?: return
        database.child("families").child(familyId!!).child("users").child(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val displayName = snapshot.child("displayName").getValue(String::class.java)
                        ?: snapshot.child("email").getValue(String::class.java) ?: "User"
                    binding.welcomeMessage.text = "Welcome, $displayName ($userRole)!"
                }

                override fun onCancelled(error: DatabaseError) {
                    binding.welcomeMessage.text = "Welcome!"
                }
            })
    }

    private fun fetchFamilyMembers() {
        val familyId = familyId ?: return
        binding.loadingProgressBar.visibility = View.VISIBLE
        database.child("families").child(familyId).child("users")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    userDisplayNames.clear()
                    for (userSnapshot in snapshot.children) {
                        val email = userSnapshot.child("email").getValue(String::class.java)
                        val displayName = snapshot.child("displayName").getValue(String::class.java)
                            ?: email
                        if (email != null && displayName != null) {
                            userDisplayNames[email] = displayName
                        }
                    }
                    fetchTasks()
                    fetchSchedules()
                    binding.loadingProgressBar.visibility = View.GONE
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error fetching users: ${error.message}")
                    Toast.makeText(this@DashboardActivity, "Error fetching users: ${error.message}", Toast.LENGTH_SHORT).show()
                    binding.loadingProgressBar.visibility = View.GONE
                }
            })
    }

    private fun fetchTasks() {
        val familyId = familyId ?: return
        binding.loadingProgressBar.visibility = View.VISIBLE
        database.child("families").child(familyId).child("tasks")
            .limitToLast(10)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    tasks.clear()
                    for (taskSnapshot in snapshot.children) {
                        val id = taskSnapshot.key ?: continue
                        val name = taskSnapshot.child("name").getValue(String::class.java) ?: ""
                        val assigneeEmail = taskSnapshot.child("assignee").getValue(String::class.java) ?: ""
                        val status = taskSnapshot.child("status").getValue(String::class.java) ?: "Assigned"
                        val assigneeDisplayName = userDisplayNames[assigneeEmail] ?: assigneeEmail
                        tasks.add(Task(id, name, assigneeDisplayName, status))
                    }
                    taskAdapter.notifyDataSetChanged()
                    binding.loadingProgressBar.visibility = View.GONE
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error fetching tasks: ${error.message}")
                    Toast.makeText(this@DashboardActivity, "Error fetching tasks: ${error.message}", Toast.LENGTH_SHORT).show()
                    binding.loadingProgressBar.visibility = View.GONE
                }
            })
    }

    private fun fetchSchedules() {
        val familyId = familyId ?: return
        binding.loadingProgressBar.visibility = View.VISIBLE
        database.child("families").child(familyId).child("schedules")
            .limitToLast(10)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    schedules.clear()
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    for (scheduleSnapshot in snapshot.children) {
                        val id = scheduleSnapshot.key ?: continue
                        val activity = scheduleSnapshot.child("activity").getValue(String::class.java) ?: ""
                        val date = scheduleSnapshot.child("date").getValue(String::class.java) ?: "N/A"
                        val time = scheduleSnapshot.child("time").getValue(String::class.java) ?: ""
                        val assigneeEmail = scheduleSnapshot.child("assignee").getValue(String::class.java) ?: ""
                        if (date == today) {
                            val assigneeDisplayName = userDisplayNames[assigneeEmail] ?: assigneeEmail
                            schedules.add(Schedule(id, activity, date, time, assigneeDisplayName))
                        }
                    }
                    scheduleAdapter.notifyDataSetChanged()
                    binding.loadingProgressBar.visibility = View.GONE
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error fetching schedules: ${error.message}")
                    Toast.makeText(this@DashboardActivity, "Error fetching schedules: ${error.message}", Toast.LENGTH_SHORT).show()
                    binding.loadingProgressBar.visibility = View.GONE
                }
            })
    }
}