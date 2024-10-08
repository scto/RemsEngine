package me.anno.bullet

import me.anno.ecs.EntityQuery.getComponentsInChildren
import me.anno.ecs.annotations.DebugProperty
import me.anno.ecs.prefab.PrefabSaveable
import me.anno.engine.serialization.SerializedProperty

class Vehicle : Rigidbody() {

    @SerializedProperty
    var suspensionStiffness = 5.88

    @SerializedProperty
    var suspensionCompression = 0.83

    @SerializedProperty
    var suspensionDamping = 0.88

    @SerializedProperty
    var maxSuspensionTravelCm = 500.0

    @SerializedProperty
    var frictionSlip = 10.5

    @DebugProperty
    val wheelCount get() = wheels.size

    val wheels get() = entity!!.getComponentsInChildren(VehicleWheel::class)

    init {
        mass = 1.0
    }

    override fun copyInto(dst: PrefabSaveable) {
        super.copyInto(dst)
        if (dst !is Vehicle) return
        dst.suspensionDamping = suspensionDamping
        dst.suspensionStiffness = suspensionStiffness
        dst.suspensionCompression = suspensionCompression
        dst.maxSuspensionTravelCm = maxSuspensionTravelCm
        dst.frictionSlip = frictionSlip
    }
}