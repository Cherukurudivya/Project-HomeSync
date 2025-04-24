package edu.cs371m.homesync

data class Task(
    val id: String,
    val name: String,
    val assignee: String,
    val status: String
) {
    override fun toString(): String {
        return "$name (Assigned to: $assignee, Status: $status)"
    }
}