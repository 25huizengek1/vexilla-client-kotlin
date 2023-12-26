package me.huizengek.vexilla.client

import kotlin.math.floor

internal fun String.hash(seed: Double): Int =
    floor(toCharArray().fold(0) { acc, c -> acc + c.code } * seed * 42).toInt() % 100
