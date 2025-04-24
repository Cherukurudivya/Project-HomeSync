package edu.cs371m.homesync

data class Schedule(
    val id: String,
    val activity: String,
    val date: String,
    val time: String,
    val assignee: String
) {
    override fun toString(): String {
        return "$activity at $time (Assigned to: $assignee)"
    }
}