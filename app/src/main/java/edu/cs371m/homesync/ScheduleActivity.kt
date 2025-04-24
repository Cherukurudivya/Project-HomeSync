package edu.cs371m.homesync

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import edu.cs371m.homesync.databinding.ActivityScheduleBinding
import java.text.SimpleDateFormat
import java.util.*
import android.Manifest
import android.content.pm.PackageManager

class ScheduleActivity : AppCompatActivity() {
    private lateinit var binding: ActivityScheduleBinding
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private val users = mutableListOf<String>()
    private val userEmails = mutableMapOf<String, String>()
    private val schedules = mutableListOf<Schedule>()
    private lateinit var scheduleAdapter: ArrayAdapter<Schedule>
    private var familyId: String? = null
    private val TAG = "ScheduleActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        familyId = intent.getStringExtra("familyId")
        if (familyId == null) {
            Toast.makeText(this, "Family ID not found", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        determineUserRole()
    }

    private fun determineUserRole() {
        val userId = auth.currentUser?.uid ?: return
        database.child("families").child(familyId!!).child("users").child(userId).child("role")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val userRole = snapshot.getValue(String::class.java) ?: "kid"
                    if (userRole == "kid") {
                        Toast.makeText(this@ScheduleActivity, "Only parents can manage schedules", Toast.LENGTH_SHORT).show()
                        finish() // Return to DashboardActivity
                        return
                    }
                    initializeActivity()
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@ScheduleActivity, "Error fetching user role: ${error.message}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            })
    }

    private fun initializeActivity() {
        createNotificationChannel()

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, users)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.assigneeSpinner.adapter = adapter

        scheduleAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, schedules)
        binding.scheduleListView.adapter = scheduleAdapter

        fetchFamilyMembers()
        fetchSchedules()

        binding.dateEditText.setOnClickListener {
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
                val date = String.format("%04d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay)
                binding.dateEditText.setText(date)
            }, year, month, day).show()
        }

        binding.timeEditText.setOnClickListener {
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            TimePickerDialog(this, { _, selectedHour: Int, selectedMinute: Int ->
                val time = String.format("%02d:%02d", selectedHour, selectedMinute)
                binding.timeEditText.setText(time)
            }, hour, minute, true).show()
        }

        binding.saveScheduleButton.setOnClickListener {
            val activity = binding.activityEditText.text.toString()
            val date = binding.dateEditText.text.toString()
            val time = binding.timeEditText.text.toString()
            val assigneeDisplayName = binding.assigneeSpinner.selectedItem?.toString() ?: ""
            val assigneeEmail = userEmails[assigneeDisplayName] ?: assigneeDisplayName
            if (activity.isNotEmpty() && date.isNotEmpty() && time.isNotEmpty() && assigneeDisplayName.isNotEmpty()) {
                saveSchedule(activity, date, time, assigneeEmail)
            } else {
                Toast.makeText(this, "Please enter all fields", Toast.LENGTH_SHORT).show()
            }
        }

        binding.backToDashboardButton.setOnClickListener {
            finish() // Return to DashboardActivity
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Schedule Reminders"
            val descriptionText = "Reminders for upcoming schedules"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("SCHEDULE_CHANNEL", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
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
                    Toast.makeText(this@ScheduleActivity, "Error fetching users: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun fetchSchedules() {
        val familyId = familyId ?: return
        database.child("families").child(familyId).child("schedules")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    schedules.clear()
                    for (scheduleSnapshot in snapshot.children) {
                        val id = scheduleSnapshot.key ?: continue
                        val activity = scheduleSnapshot.child("activity").getValue(String::class.java) ?: ""
                        val date = scheduleSnapshot.child("date").getValue(String::class.java) ?: "N/A"
                        val time = scheduleSnapshot.child("time").getValue(String::class.java) ?: ""
                        val assigneeEmail = scheduleSnapshot.child("assignee").getValue(String::class.java) ?: ""
                        val assigneeDisplayName = users.find { userEmails[it] == assigneeEmail } ?: assigneeEmail
                        schedules.add(Schedule(id, activity, date, time, assigneeDisplayName))
                    }
                    scheduleAdapter.notifyDataSetChanged()
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@ScheduleActivity, "Error fetching schedules: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun saveSchedule(activity: String, date: String, time: String, assigneeEmail: String) {
        val familyId = familyId ?: return
        val scheduleId = database.child("families").child(familyId).child("schedules").push().key ?: return
        val schedule = mapOf(
            "activity" to activity,
            "date" to date,
            "time" to time,
            "assignee" to assigneeEmail
        )
        database.child("families").child(familyId).child("schedules").child(scheduleId)
            .setValue(schedule)
            .addOnSuccessListener {
                Toast.makeText(this, "Schedule saved", Toast.LENGTH_SHORT).show()
                binding.activityEditText.text.clear()
                binding.dateEditText.text.clear()
                binding.timeEditText.text.clear()
                scheduleNotification(scheduleId, activity, date, time, assigneeEmail)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error saving schedule", Toast.LENGTH_SHORT).show()
            }
    }

    /*private fun deleteScheduledActivity(taskId: String) {
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
    } */

    private fun scheduleNotification(scheduleId: String, activity: String, date: String, time: String, assigneeEmail: String) {
        try {
            // Check POST_NOTIFICATIONS permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "POST_NOTIFICATIONS permission not granted")
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            "Please enable notifications in app settings to receive schedule reminders",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return
                }
            }

            // Use UTC timezone for parsing
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            sdf.isLenient = false
            Log.d(TAG, "Parsing schedule: $date $time")
            val scheduleTime = sdf.parse("$date $time")
            if (scheduleTime == null) {
                Log.e(TAG, "Failed to parse date and time: $date $time")
                runOnUiThread {
                    Toast.makeText(this, "Invalid date or time format", Toast.LENGTH_SHORT).show()
                }
                return
            }

            // Convert to local timezone for notification
            val calendar = Calendar.getInstance(TimeZone.getDefault())
            calendar.time = scheduleTime
            calendar.add(Calendar.MINUTE, -15)
            val notificationTime = calendar.timeInMillis
            val localSdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).apply {
                timeZone = TimeZone.getDefault()
            }
            Log.d(TAG, "Event time: ${localSdf.format(scheduleTime)}, Notification time: ${localSdf.format(Date(notificationTime))}")

            // Validate notification time is in the future
            val currentTime = System.currentTimeMillis()
            if (notificationTime < currentTime) {
                Log.d(TAG, "Notification time is in the past: $notificationTime (current: $currentTime)")
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Cannot schedule notification: Time is too close to now. Please select a time at least 15 minutes in the future.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return
            }

            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    Log.w(TAG, "SCHEDULE_EXACT_ALARM permission not granted")
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            "Please enable exact alarms in settings to receive schedule reminders",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return
                }
            }

            val assigneeDisplayName = users.find { userEmails[it] == assigneeEmail } ?: assigneeEmail
            val intent = Intent(this, NotificationReceiver::class.java).apply {
                putExtra("activity", activity)
                putExtra("assignee", assigneeDisplayName)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                scheduleId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                notificationTime,
                pendingIntent
            )
            Log.d(TAG, "Scheduled notification for $activity at $notificationTime")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling notification: ${e.message}", e)
            runOnUiThread {
                Toast.makeText(this, "Error scheduling notification", Toast.LENGTH_SHORT).show()
            }
        }
    }
}