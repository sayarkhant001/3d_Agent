package com.example.logic

object NumberGenerator {
    // ထိပ် (Head): Generates all 100 numbers starting with the digit
    fun head(d: Int): List<String> = (0..99).map { "$d${it.toString().padStart(2, '0')}" }

    // လယ် (Middle): Generates all 100 numbers with the digit in the middle
    fun middle(d: Int): List<String> = (0..99).map { val s = it.toString().padStart(2, '0'); "${s[0]}$d${s[1]}" }

    // ပိတ် (Tail/Close): Generates all 100 numbers ending with the digit
    fun tail(d: Int): List<String> = (0..99).map { "${it.toString().padStart(2, '0')}$d" }

    // ရှေ့ပူး (Front double): First two digits are the same
    fun frontDouble(): List<String> = (0..9).flatMap { i -> (0..9).map { j -> "$i$i$j" } }

    // နောက်ပူး (Back double): Last two digits are the same
    fun backDouble(): List<String> = (0..9).flatMap { i -> (0..9).map { j -> "$i$j$j" } }

    // အခွ (Cycle): First and last digits are the same
    fun cycle(): List<String> = (0..9).flatMap { i -> (0..9).map { j -> "$i$j$i" } }

    // ရှေ့စီးရီး (Front series): Appends 0-9 to the front of a 2-digit input
    fun frontSeries(d1: Int, d2: Int): List<String> = (0..9).map { "$it$d1$d2" }

    // လယ်စီးရီး (Middle series): Inserts 0-9 in the middle of a 2-digit input
    fun middleSeries(d1: Int, d2: Int): List<String> = (0..9).map { "$d1$it$d2" }

    // နောက်စီးရီး (Back series): Appends 0-9 to the back of a 2-digit input
    fun backSeries(d1: Int, d2: Int): List<String> = (0..9).map { "$d1$d2$it" }

    // ဘရိတ် (Break): Sum of three digits (modulo 10) equals the input
    fun breakNum(d: Int): List<String> = (0..999)
        .map { it.toString().padStart(3, '0') }
        .filter { s -> s.sumOf { it.digitToInt() } % 10 == d }

    // ထွိုင် (Tri): Exact triple numbers
    fun tri(): List<String> = (0..9).map { "$it$it$it" }

    // အပါ (Include): Any number containing the digit
    fun include(d: Int): List<String> = (0..999)
        .map { it.toString().padStart(3, '0') }
        .filter { it.contains(d.toString()) }

    // R (Permutations): All unique permutations of a given 3-digit string
    fun permutations(str: String): List<String> {
        if (str.length != 3) return emptyList()
        val set = mutableSetOf<String>()
        for (i in 0..2) {
            for (j in 0..2) {
                for (k in 0..2) {
                    if (i != j && i != k && j != k) {
                        set.add("${str[i]}${str[j]}${str[k]}")
                    }
                }
            }
        }
        return set.toList()
    }
}
