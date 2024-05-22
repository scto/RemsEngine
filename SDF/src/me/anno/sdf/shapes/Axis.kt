package me.anno.sdf.shapes

enum class Axis(val id: Int) {
    X(0), Y(1), Z(2);
    val char = 'x' + id
}