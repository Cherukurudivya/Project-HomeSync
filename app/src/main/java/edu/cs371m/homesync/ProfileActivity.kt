package edu.cs371m.homesync

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import edu.cs371m.homesync.databinding.ActivityProfileBinding

class ProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProfileBinding
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private var familyId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference
        familyId = intent.getStringExtra("familyId")
        if (familyId == null) {
            Toast.makeText(this, "Family ID not found", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        fetchCurrentDisplayName()

        binding.saveProfileButton.setOnClickListener {
            val displayName = binding.displayNameEditText.text.toString()
            if (displayName.isNotEmpty()) {
                saveDisplayName(displayName)
            } else {
                Toast.makeText(this, "Please enter a display name", Toast.LENGTH_SHORT).show()
            }
        }

        binding.backToDashboardButton.setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java).apply {
                putExtra("familyId", familyId)
            }
            startActivity(intent)
            finish()
        }
    }

    private fun fetchCurrentDisplayName() {
        val userId = auth.currentUser?.uid ?: return
        database.child("families").child(familyId!!).child("users").child(userId).child("displayName")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val displayName = snapshot.getValue(String::class.java)
                    if (displayName != null) {
                        binding.displayNameEditText.setText(displayName)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@ProfileActivity, "Error fetching profile: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun saveDisplayName(displayName: String) {
        val userId = auth.currentUser?.uid ?: return
        database.child("families").child(familyId!!).child("users").child(userId).child("displayName")
            .setValue(displayName)
            .addOnSuccessListener {
                Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error updating profile", Toast.LENGTH_SHORT).show()
            }
    }
}