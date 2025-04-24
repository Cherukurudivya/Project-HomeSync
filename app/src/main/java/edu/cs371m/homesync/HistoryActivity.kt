package edu.cs371m.homesync

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*
import edu.cs371m.homesync.databinding.ActivityHistoryBinding
import java.text.SimpleDateFormat
import java.util.*

data class HistoryEntry(val id: String, val taskId: String, val name: String, val assignee: String, val completedAt: Long) {
    override fun toString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val date = sdf.format(Date(completedAt))
        return "$name (Assigned to: $assignee, Completed: $date)"
    }
}

class HistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHistoryBinding
    private lateinit var database: DatabaseReference
    private val history = mutableListOf<HistoryEntry>()
    private lateinit var historyAdapter: ArrayAdapter<HistoryEntry>
    private var familyId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = FirebaseDatabase.getInstance().reference
        familyId = intent.getStringExtra("familyId")
        if (familyId == null) {
            Toast.makeText(this, "Family ID not found", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        historyAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, history)
        binding.historyListView.adapter = historyAdapter

        fetchHistory()

        binding.backToDashboardButton.setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java).apply {
                putExtra("familyId", familyId)
            }
            startActivity(intent)
            finish()
        }
    }

    private fun fetchHistory() {
        val familyId = familyId ?: return
        database.child("families").child(familyId).child("history")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    history.clear()
                    for (historySnapshot in snapshot.children) {
                        val id = historySnapshot.key ?: continue
                        val taskId = historySnapshot.child("taskId").getValue(String::class.java) ?: ""
                        val name = historySnapshot.child("name").getValue(String::class.java) ?: ""
                        val assignee = historySnapshot.child("assignee").getValue(String::class.java) ?: ""
                        val completedAt = historySnapshot.child("completedAt").getValue(String::class.java)?.toLong() ?: 0L
                        history.add(HistoryEntry(id, taskId, name, assignee, completedAt))
                    }
                    historyAdapter.notifyDataSetChanged()
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@HistoryActivity, "Error fetching history: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }
}