package com.z8dn.plugins.a2pt.settings

import com.z8dn.plugins.a2pt.utils.AndroidViewNodeUtils

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.math.BigDecimal

/**
 * Reads and writes file groups as JSON, and holds the rules an imported file must satisfy.
 *
 * Deliberately free of UI classes: failures are reported as data ([ImportResult.Failure]) and
 * the settings page turns them into messages.
 *
 * Shape: an object with an integer `version` and a `groups` array of objects, each with a
 * `name` string and a `patterns` string array (exclusions prefixed with `!`).
 */
object ProjectFileGroupsSerializer {

    /** The format version written by this build, and the newest one it can read. */
    const val CURRENT_VERSION = 1

    /** Default file name, kept under [DEFAULT_DIRECTORY] so it can be committed with a project. */
    const val DEFAULT_FILE_NAME = "file-groups.json"
    const val DEFAULT_DIRECTORY = ".a2pt"

    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    /** Why a single group is not acceptable. Shared by the group dialog and import. */
    sealed class GroupProblem {
        data object EmptyName : GroupProblem()
        data object NoPatterns : GroupProblem()

        /** Only produced by import; the dialog silently drops blank rows instead. */
        data object BlankPattern : GroupProblem()
        data object NoInclusionPattern : GroupProblem()
        data class InvalidGlob(val pattern: String) : GroupProblem()
    }

    sealed class ImportResult {
        data class Success(val groups: List<ProjectFileGroup>) : ImportResult()
        data class Failure(val reason: FailureReason) : ImportResult()
    }

    sealed class FailureReason {
        /** The text is not JSON at all. */
        data class MalformedJson(val detail: String?) : FailureReason()

        /** Valid JSON, but not shaped like a group file (no object, no `groups` array, wrong types). */
        data class InvalidStructure(val detail: String) : FailureReason()

        /** No `version`, or one that is not a positive integer. */
        data object MissingVersion : FailureReason()

        /** Written by a newer version of the plugin than this one. */
        data class NewerVersion(val version: Int) : FailureReason()

        /** The group at [index] (zero-based) breaks one of the group rules. [name] is as written. */
        data class InvalidGroup(val index: Int, val name: String, val problem: GroupProblem) : FailureReason()

        /** Two groups in the file share [name]. */
        data class DuplicateName(val name: String) : FailureReason()
    }

    fun toJson(groups: List<ProjectFileGroup>): String {
        val root = JsonObject()
        root.addProperty("version", CURRENT_VERSION)
        val array = JsonArray()
        for (group in groups) {
            val entry = JsonObject()
            entry.addProperty("name", group.groupName)
            val patterns = JsonArray()
            group.patterns.forEach { patterns.add(it) }
            entry.add("patterns", patterns)
            array.add(entry)
        }
        root.add("groups", array)
        return gson.toJson(root) + "\n"
    }

    fun fromJson(text: String): ImportResult {
        val root = try {
            JsonParser.parseString(text)
        } catch (e: JsonParseException) {
            return failure(FailureReason.MalformedJson(e.message))
        } catch (e: StackOverflowError) {
            return failure(FailureReason.MalformedJson(null))
        }
        if (!root.isJsonObject) {
            return if (root.isJsonNull) {
                failure(FailureReason.MalformedJson(null))
            } else {
                failure(FailureReason.InvalidStructure("top level is not an object"))
            }
        }
        val obj = root.asJsonObject

        val version = readVersion(obj["version"]) ?: return failure(FailureReason.MissingVersion)
        if (version > CURRENT_VERSION) return failure(FailureReason.NewerVersion(version))

        val groupsElement = obj["groups"]
        if (groupsElement == null || !groupsElement.isJsonArray) {
            return failure(FailureReason.InvalidStructure("\"groups\" is missing or not an array"))
        }

        val groups = mutableListOf<ProjectFileGroup>()
        groupsElement.asJsonArray.forEachIndexed { index, element ->
            if (!element.isJsonObject) {
                return failure(FailureReason.InvalidStructure("group ${index + 1} is not an object"))
            }
            val entry = element.asJsonObject
            val name = readString(entry["name"])
                ?: return failure(FailureReason.InvalidStructure("group ${index + 1} has no \"name\" string"))
            val patternsElement = entry["patterns"]
            if (patternsElement == null || !patternsElement.isJsonArray) {
                return failure(FailureReason.InvalidStructure("group ${index + 1} has no \"patterns\" array"))
            }
            val patterns = mutableListOf<String>()
            for (p in patternsElement.asJsonArray) {
                patterns.add(
                    readString(p)
                        ?: return failure(FailureReason.InvalidStructure("group ${index + 1} has a non-string pattern"))
                )
            }

            val trimmedName = name.trim()
            // Blank patterns are rejected rather than dropped: the file is meant to be exact.
            val trimmedPatterns = patterns.map { it.trim() }
            val problem = if (trimmedPatterns.any { it.isEmpty() }) {
                GroupProblem.BlankPattern
            } else {
                validateGroup(trimmedName, trimmedPatterns)
            }
            if (problem != null) {
                return failure(FailureReason.InvalidGroup(index, name, problem))
            }
            groups.add(ProjectFileGroup(trimmedName, trimmedPatterns.toMutableList()))
        }

        // Same rule as the group dialog: names are compared trimmed and case-insensitively.
        val seen = mutableSetOf<String>()
        for (group in groups) {
            if (!seen.add(group.groupName.lowercase())) {
                return failure(FailureReason.DuplicateName(group.groupName))
            }
        }
        return ImportResult.Success(groups)
    }

    /**
     * The rules every group must satisfy: a name, at least one pattern, at least one inclusion
     * (a pattern not prefixed with `!`), and every glob compiling. Inputs should already be trimmed
     * and, for the dialog, stripped of blank patterns. Returns the first problem, or null.
     */
    fun validateGroup(name: String, patterns: List<String>): GroupProblem? {
        if (name.trim().isEmpty()) return GroupProblem.EmptyName
        if (patterns.isEmpty()) return GroupProblem.NoPatterns
        if (patterns.all { it.startsWith("!") }) return GroupProblem.NoInclusionPattern
        patterns.firstOrNull { !AndroidViewNodeUtils.isValidGlob(it.removePrefix("!")) }
            ?.let { return GroupProblem.InvalidGlob(it) }
        return null
    }

    /**
     * Merges [imported] into [existing]: an imported group replaces the existing group with the
     * same name (trimmed, case-insensitive) in place; the rest are appended in file order.
     */
    fun merge(existing: List<ProjectFileGroup>, imported: List<ProjectFileGroup>): List<ProjectFileGroup> {
        fun key(group: ProjectFileGroup) = group.groupName.trim().lowercase()
        val importedByName = imported.associateBy { key(it) }
        val replaced = mutableSetOf<String>()
        val result = existing.map { current ->
            val replacement = importedByName[key(current)]
            if (replacement != null) {
                replaced.add(key(current))
                replacement
            } else {
                current
            }
        }.toMutableList()
        imported.filter { key(it) !in replaced }.forEach { result.add(it) }
        return result
    }

    private fun failure(reason: FailureReason) = ImportResult.Failure(reason)

    private fun readVersion(element: JsonElement?): Int? {
        if (element !is JsonPrimitive || !element.isNumber) return null
        val decimal = try {
            element.asBigDecimal
        } catch (e: NumberFormatException) {
            return null
        }
        if (decimal.signum() <= 0) return null
        val integral = decimal.stripTrailingZeros()
        if (integral.scale() > 0) return null
        // Absurdly large versions are still "newer".
        return if (integral > BigDecimal(Int.MAX_VALUE)) Int.MAX_VALUE else integral.toInt()
    }

    private fun readString(element: JsonElement?): String? =
        if (element is JsonPrimitive && element.isString) element.asString else null
}
