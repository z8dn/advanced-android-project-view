package com.z8dn.plugins.a2pt

/**
 * Represents a custom node grouping configuration.
 * Each grouping has a name and a list of file patterns.
 *
 * @param name The display name of the grouping node
 * @param patterns List of file patterns (e.g., "*.md", "LICENSE") that belong to this group
 */
data class CustomNodeGrouping(
    var name: String = "",
    var patterns: MutableList<String> = mutableListOf()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CustomNodeGrouping) return false
        return name == other.name && patterns == other.patterns
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + patterns.hashCode()
        return result
    }
}
