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
import androidx.recyclerview.widget.LinearLayoutManager
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
    private val upcomingSchedules = mutableListOf<Schedule>()
    private val pastSchedules = mutableListOf<Schedule>()
    private lateinit var scheduleAdapter: ScheduleRecyclerAdapter
    private lateinit var historyAdapter: HistoryRecyclerAdapter
    private var familyId: String? = null
    private var userRole: String? = null
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
                    userRole = snapshot.getValue(String::class.java) ?: "kid"
                    if (userRole == "kid") {
                        Toast.makeText(this@ScheduleActivity, "Only parents can manage schedules", Toast.LENGTH_SHORT).show()
                        finish()
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

        scheduleAdapter = ScheduleRecyclerAdapter(upcomingSchedules, userRole ?: "parent") { scheduleId ->
            deleteSchedule(scheduleId)
        }
        binding.scheduleRecyclerView.adapter = scheduleAdapter
        binding.scheduleRecyclerView.layoutManager = LinearLayoutManager(this)

        historyAdapter = HistoryRecyclerAdapter(pastSchedules)
        binding.historyRecyclerView.adapter = historyAdapter
        binding.historyRecyclerView.layoutManager = LinearLayoutManager(this)

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
            finish()
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
                    upcomingSchedules.clear()
                    pastSchedules.clear()
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).apply {
                        timeZone = TimeZone.getTimeZone("America/Chicago")
                    }
                    val todaySdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
                        timeZone = TimeZone.getTimeZone("America/Chicago")
                    }
                    val currentCalendar = Calendar.getInstance(TimeZone.getTimeZone("America/Chicago"))
                    val today = todaySdf.format(currentCalendar.time)
                    val currentHour = currentCalendar.get(Calendar.HOUR_OF_DAY)
                    val currentMinute = currentCalendar.get(Calendar.MINUTE)
                    val currentTimeInMinutes = currentHour * 60 + currentMinute

                    for (scheduleSnapshot in snapshot.children) {
                        val id = scheduleSnapshot.key ?: continue
                        val activity = scheduleSnapshot.child("activity").getValue(String::class.java) ?: ""
                        val date = scheduleSnapshot.child("date").getValue(String::class.java) ?: "N/A"
                        val time = scheduleSnapshot.child("time").getValue(String::class.java) ?: ""
                        val assigneeEmail = scheduleSnapshot.child("assignee").getValue(String::class.java) ?: ""
                        val assigneeDisplayName = users.find { userEmails[it] == assigneeEmail } ?: assigneeEmail

                        try {
                            val scheduleTime = sdf.parse("$date $time")
                            if (scheduleTime != null) {
                                val calendar = Calendar.getInstance(TimeZone.getTimeZone("America/Chicago"))
                                calendar.time = scheduleTime
                                val scheduleDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(scheduleTime)
                                val scheduleHour = calendar.get(Calendar.HOUR_OF_DAY)
                                val scheduleMinute = calendar.get(Calendar.MINUTE)
                                val scheduleTimeInMinutes = scheduleHour * 60 + scheduleMinute

                                val schedule = Schedule(id, activity, date, time, assigneeDisplayName)
                                if (scheduleDate < today || (scheduleDate == today && scheduleTimeInMinutes < currentTimeInMinutes)) {
                                    pastSchedules.add(schedule)
                                } else {
                                    upcomingSchedules.add(schedule)
                                }
                            } else {
                                Log.e(TAG, "Failed to parse schedule time: $date $time")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing schedule: ${e.message}")
                        }
                    }
                    scheduleAdapter.notifyDataSetChanged()
                    historyAdapter.notifyDataSetChanged()
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@ScheduleActivity, "Error fetching schedules: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun saveSchedule(activity: String, date: String, time: String, assigneeEmail: String) {
        val familyId = familyId ?: return

        val validatedDate = if (date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            date
        } else {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("America/Chicago")
            }.format(Date())
        }
        if (!time.matches(Regex("\\d{2}:\\d{2}"))) {
            Toast.makeText(this, "Invalid time format (use HH:MM, e.g., 21:10 CDT)", Toast.LENGTH_SHORT).show()
            return
        }
        val scheduleId = database.child("families").child(familyId).child("schedules").push().key ?: return
        val schedule = mapOf(
            "activity" to activity,
            "date" to validatedDate,
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
                scheduleNotification(scheduleId, activity, validatedDate, time, assigneeEmail)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error saving schedule", Toast.LENGTH_SHORT).show()
            }
    }

    private fun scheduleNotification(scheduleId: String, activity: String, date: String, time: String, assigneeEmail: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "POST_NOTIFICATIONS permission not granted")
                    runOnUiThread {
                        Toast.makeText(this, "Enable notifications in settings for reminders", Toast.LENGTH_LONG).show()
                    }
                    return
                }
            }

            // Validate inputs
            if (!date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) || !time.matches(Regex("\\d{2}:\\d{2}"))) {
                Log.e(TAG, "Invalid format: date=$date, time=$time")
                runOnUiThread {
                    Toast.makeText(this, "Invalid format (use YYYY-MM-DD HH:MM CDT)", Toast.LENGTH_SHORT).show()
                }
                return
            }

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("America/Chicago")
            }
            sdf.isLenient = false
            Log.d(TAG, "Input: date=$date, time=$time, Device timezone: ${TimeZone.getDefault().id}")
            val scheduleTime = sdf.parse("$date $time")
            if (scheduleTime == null) {
                Log.e(TAG, "Failed to parse: $date $time")
                runOnUiThread {
                    Toast.makeText(this, "Invalid date/time format", Toast.LENGTH_SHORT).show()
                }
                return
            }

            val calendar = Calendar.getInstance(TimeZone.getTimeZone("America/Chicago"))
            calendar.time = scheduleTime
            calendar.add(Calendar.MINUTE, -15)
            val notificationTime = calendar.timeInMillis

            // Get current time in CDT
            val currentCalendar = Calendar.getInstance(TimeZone.getTimeZone("America/Chicago"))
            val currentTime = currentCalendar.timeInMillis

            val localSdf = SimpleDateFormat("yyyy-MM-dd HH:mm z", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("America/Chicago")
            }
            Log.d(TAG, "Event: ${localSdf.format(scheduleTime)}, Notification: ${localSdf.format(Date(notificationTime))}, Current: ${localSdf.format(Date(currentTime))}")

            val minNotificationTime = currentTime + 5 * 60 * 1000 // 5 minutes
            if (notificationTime < currentTime) {
                Log.d(TAG, "Notification in past: $notificationTime (current: $currentTime)")
                runOnUiThread {
                    Toast.makeText(this, "Cannot schedule: Event has passed.", Toast.LENGTH_LONG).show()
                }
                return
            } else if (notificationTime < minNotificationTime) {
                Log.d(TAG, "Notification too close: $notificationTime (min: $minNotificationTime)")
                runOnUiThread {
                    Toast.makeText(this, "Scheduled, but less than 15 min away. Allow 5 min.", Toast.LENGTH_LONG).show()
                }
            }

            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    Log.w(TAG, "SCHEDULE_EXACT_ALARM permission not granted")
                    runOnUiThread {
                        Toast.makeText(this, "Enable exact alarms in settings", Toast.LENGTH_LONG).show()
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

    private fun deleteSchedule(scheduleId: String) {
        if (userRole != "parent") {
            Toast.makeText(this, "Only parents can delete schedules", Toast.LENGTH_SHORT).show()
            return
        }
        val familyId = familyId ?: return
        database.child("families").child(familyId).child("schedules").child(scheduleId)
            .removeValue()
            .addOnSuccessListener {
                Toast.makeText(this, "Schedule deleted", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error deleting schedule", Toast.LENGTH_SHORT).show()
            }
    }
}