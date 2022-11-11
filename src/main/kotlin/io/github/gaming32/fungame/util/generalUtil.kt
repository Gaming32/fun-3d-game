package io.github.gaming32.fungame.util

fun simpleParentDir(path: String): String {
    val slash = path.indexOf('/')
    if (slash == -1) {
        return ""
    }
    return path.substring(0, slash + 1)
}

fun CharSequence.count(c: Char) = count { it == c }
