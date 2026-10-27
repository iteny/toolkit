package com.example.toolkit.ui.tools

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlTable
import org.yaml.snakeyaml.Yaml

internal fun yamlToJson(text: String): String =
    prettyJson(jsonFromAny(Yaml().load<Any?>(text)))

internal fun jsonToYaml(json: String): String {
    val value = jsonToAny(JSONTokener(json).nextValue())
    return Yaml().dump(value)
}

internal fun tomlToJson(text: String): String {
    val result = Toml.parse(text)
    if (result.hasErrors()) {
        val first = result.errors().firstOrNull()?.toString() ?: "语法错误"
        error("TOML 解析失败：$first")
    }
    return prettyJson(jsonFromAny(tomlValue(result.toMap())))
}

internal fun jsonToToml(json: String): String {
    val root = JSONTokener(json).nextValue()
    val out = StringBuilder()
    when (root) {
        is JSONObject -> writeTomlTable(out, root, "", header = false)
        is JSONArray -> {
            val objectsOnly = root.length() > 0 &&
                (0 until root.length()).all { root.get(it) is JSONObject }
            if (objectsOnly) {
                for (index in 0 until root.length()) {
                    val item = root.optJSONObject(index) ?: continue
                    out.append('\n').append("[[items]]\n")
                    writeTomlTable(out, item, "items", header = false)
                }
            } else {
                out.append("items = ").append(tomlValueString(root)).append('\n')
            }
        }

        else -> out.append("value = ").append(tomlValueString(root)).append('\n')
    }
    return out.toString().trimEnd() + "\n"
}

/* ------------------------------------------------------------------ */
/* JSON <-> generic values                                            */
/* ------------------------------------------------------------------ */

private fun jsonFromAny(value: Any?): Any = when (value) {
    null -> JSONObject.NULL
    is Map<*, *> -> JSONObject().also { obj ->
        value.forEach { (key, item) -> obj.put(key.toString(), jsonFromAny(item)) }
    }

    is Iterable<*> -> JSONArray().also { array ->
        value.forEach { array.put(jsonFromAny(it)) }
    }

    is Array<*> -> JSONArray().also { array ->
        value.forEach { array.put(jsonFromAny(it)) }
    }

    is String, is Number, is Boolean -> value
    else -> value.toString()
}

private fun prettyJson(value: Any): String = when (value) {
    is JSONObject -> value.toString(2)
    is JSONArray -> value.toString(2)
    is String -> JSONObject.quote(value)
    JSONObject.NULL -> "null"
    else -> value.toString()
}

private fun jsonToAny(value: Any?): Any? = when (value) {
    null, JSONObject.NULL -> null
    is JSONObject -> LinkedHashMap<String, Any?>().also { map ->
        val iterator = value.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            map[key] = jsonToAny(value.opt(key))
        }
    }

    is JSONArray -> ArrayList<Any?>().also { list ->
        for (index in 0 until value.length()) list += jsonToAny(value.get(index))
    }

    else -> value
}

private fun tomlValue(value: Any?): Any? = when (value) {
    is TomlTable -> value.toMap().mapValues { tomlValue(it.value) }
    is TomlArray -> (0 until value.size()).map { tomlValue(value.get(it)) }
    is Map<*, *> -> value.mapValues { tomlValue(it.value) }
    is List<*> -> value.map { tomlValue(it) }
    is Array<*> -> value.map { tomlValue(it) }
    else -> value
}

/* ------------------------------------------------------------------ */
/* TOML writer                                                        */
/* ------------------------------------------------------------------ */

private fun writeTomlTable(
    out: StringBuilder,
    obj: JSONObject,
    path: String,
    header: Boolean,
) {
    if (header) {
        if (out.isNotEmpty()) out.append('\n')
        out.append('[').append(path).append("]\n")
    }

    val tables = ArrayList<Pair<String, JSONObject>>()
    val tableArrays = ArrayList<Pair<String, JSONArray>>()
    val iterator = obj.keys()
    while (iterator.hasNext()) {
        val key = iterator.next()
        val value = obj.opt(key)
        when {
            value is JSONObject -> tables += key to value
            value is JSONArray && value.length() > 0 && (0 until value.length()).all { value.get(it) is JSONObject } ->
                tableArrays += key to value

            else -> {
                out.append(tomlKey(key)).append(" = ").append(tomlValueString(value)).append('\n')
            }
        }
    }

    tables.forEach { (key, value) ->
        writeTomlTable(out, value, joinPath(path, key), header = true)
    }

    tableArrays.forEach { (key, array) ->
        val childPath = joinPath(path, key)
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            out.append('\n').append("[[").append(childPath).append("]]\n")
            writeTomlTable(out, item, childPath, header = false)
        }
    }
}

private fun joinPath(path: String, key: String): String =
    if (path.isBlank()) tomlKey(key) else "$path.${tomlKey(key)}"

private fun tomlKey(key: String): String =
    if (Regex("^[A-Za-z0-9_-]+$").matches(key)) key else tomlString(key)

private fun tomlValueString(value: Any?): String = when (value) {
    null, JSONObject.NULL -> "\"\""
    is String -> tomlString(value)
    is Boolean -> value.toString()
    is Number -> value.toString()
    is JSONArray -> (0 until value.length()).joinToString(", ", "[", "]") {
        tomlValueString(value.opt(it))
    }

    is JSONObject -> value.keys().asSequence().joinToString(", ", "{ ", " }") { key ->
        "${tomlKey(key)} = ${tomlValueString(value.opt(key))}"
    }

    else -> tomlString(value.toString())
}

private fun tomlString(value: String): String = buildString {
    append('"')
    value.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (char.code >= 0x20) append(char)
        }
    }
    append('"')
}
