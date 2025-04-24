package edu.cs371m.homesync

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.*
import com.google.firebase.ktx.Firebase
import edu.cs371m.homesync.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth
        database = FirebaseDatabase.getInstance().reference

        auth.signOut()
        Log.d(TAG, "Signed out on app launch")

        val roles = listOf("parent", "kid")
        val roleAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, roles)
        roleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.roleSpinner.adapter = roleAdapter

        setupLogin()
    }

    private fun setupLogin() {
        binding.loginButton.setOnClickListener {
            val email = binding.emailEditText.text.toString()
            val password = binding.passwordEditText.text.toString()
            val familyCode = binding.familyCodeEditText.text.toString()
            val role = binding.roleSpinner.selectedItem?.toString() ?: "kid"
            if (email.isNotEmpty() && password.isNotEmpty() && familyCode.isNotEmpty()) {
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            Log.d(TAG, "Login successful: $email")
                            Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()
                            val user = auth.currentUser
                            if (user != null) {
                                addUserToFamily(familyCode, user.uid, email, role)
                                addTestDatabaseValue(familyCode)
                                val intent = Intent(this, DashboardActivity::class.java)
                                intent.putExtra("familyId", familyCode)
                                intent.putExtra("userRole", role)
                                startActivity(intent)
                                finish()
                            } else {
                                Log.e(TAG, "Login succeeded but no user found")
                                Toast.makeText(this, "Error: No user found", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Log.w(TAG, "Login failed", task.exception)
                            Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            } else {
                Toast.makeText(this, "Please enter email, password, and family code", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addUserToFamily(familyCode: String, userId: String, email: String, role: String) {
        database.child("families").child(familyCode).child("users").child(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        val userData = mapOf(
                            "email" to email,
                            "role" to role,
                            "displayName" to email.split("@")[0] // Optional: Add displayName
                        )
                        database.child("families").child(familyCode).child("users").child(userId)
                            .setValue(userData)
                            .addOnSuccessListener {
                                Log.d(TAG, "User added to family: $email as $role")
                            }
                            .addOnFailureListener { error ->
                                Log.e(TAG, "Failed to add user: ${error.message}")
                                Toast.makeText(this@MainActivity, "Failed to add user: ${error.message}", Toast.LENGTH_LONG).show()
                            }
                    } else {
                        Log.d(TAG, "User already exists in family: $email")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error checking user: ${error.message}")
                    Toast.makeText(this@MainActivity, "Error checking user: ${error.message}", Toast.LENGTH_LONG).show()
                }
            })
    }

    private fun addTestDatabaseValue(familyCode: String) {
        val testData = mapOf("testKey" to "testValue")
        database.child("families").child(familyCode).child("test")
            .setValue(testData)
            .addOnSuccessListener {
                Log.d(TAG, "Test value added to database")
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Failed to add test value: ${error.message}")
                Toast.makeText(this, "Failed to add test value: ${error.message}", Toast.LENGTH_LONG).show()
            }
    }
}