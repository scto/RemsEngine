package me.anno.io.binary

import me.anno.io.Streams.writeBE16
import me.anno.io.Streams.writeBE32
import me.anno.io.Streams.writeBE32F
import me.anno.io.Streams.writeBE64
import me.anno.io.Streams.writeBE64F
import me.anno.io.base.BaseWriter
import me.anno.io.binary.BinaryTypes.OBJECTS_HOMOGENOUS_ARRAY
import me.anno.io.binary.BinaryTypes.OBJECT_ARRAY
import me.anno.io.binary.BinaryTypes.OBJECT_ARRAY_2D
import me.anno.io.binary.BinaryTypes.OBJECT_IMPL
import me.anno.io.binary.BinaryTypes.OBJECT_LIST_UNKNOWN_LENGTH
import me.anno.io.binary.BinaryTypes.OBJECT_NULL
import me.anno.io.binary.BinaryTypes.OBJECT_PTR
import me.anno.io.files.FileReference
import me.anno.io.files.InvalidRef
import me.anno.io.json.saveable.SimpleType
import me.anno.io.saveable.Saveable
import me.anno.utils.types.Booleans.toInt
import org.joml.AABBd
import org.joml.AABBf
import org.joml.Matrix2d
import org.joml.Matrix2f
import org.joml.Matrix3d
import org.joml.Matrix3f
import org.joml.Matrix3x2d
import org.joml.Matrix3x2f
import org.joml.Matrix4d
import org.joml.Matrix4f
import org.joml.Matrix4x3d
import org.joml.Matrix4x3f
import org.joml.Planed
import org.joml.Planef
import org.joml.Quaterniond
import org.joml.Quaternionf
import org.joml.Vector2d
import org.joml.Vector2f
import org.joml.Vector2i
import org.joml.Vector3d
import org.joml.Vector3f
import org.joml.Vector3i
import org.joml.Vector4d
import org.joml.Vector4f
import org.joml.Vector4i
import java.io.OutputStream

class BinaryWriter(val output: OutputStream) : BaseWriter(true) {

    /**
     * max number of strings? idk...
     * typically we need only a few, but what if we need many?
     * */
    private val knownStrings = HashMap<String, Int>()

    private val knownNameTypes = HashMap<String, HashMap<NameType, Int>>()

    private var currentClass = ""
    private var currentNameTypes = knownNameTypes.getOrPut(currentClass, ::HashMap)

    private fun usingType(type: String, run: () -> Unit) {
        val old1 = currentClass
        val old2 = currentNameTypes
        currentClass = type
        currentNameTypes = knownNameTypes.getOrPut(type) { HashMap() }
        run()
        currentClass = old1
        currentNameTypes = old2
    }

    private fun writeEfficientString(string: String?) {
        if (string == null) {
            output.writeBE32(-1)
        } else {
            val known = knownStrings[string] ?: -1
            if (known >= 0) {
                output.writeBE32(known)
            } else {
                val bytes = string.encodeToByteArray()
                output.writeBE32(-2 - bytes.size)
                output.write(bytes)
                knownStrings[string] = knownStrings.size
            }
        }
    }

    private fun writeTypeString(value: String) {
        writeEfficientString(value)
    }

    private fun writeAttributeStart(name: String, type: Int) {
        val nameType = NameType(name, type)
        val id = currentNameTypes[nameType] ?: -1
        if (id >= 0) {
            // known -> shortcut
            output.writeBE32(id)
        } else {
            // not previously known -> create a new one
            output.writeBE32(-1)
            val newId = currentNameTypes.size
            currentNameTypes[nameType] = newId
            writeTypeString(name)
            output.write(type)
        }
    }

    override fun writeBoolean(name: String, value: Boolean, force: Boolean) {
        if (force || value) {
            writeAttributeStart(name, SimpleType.BOOLEAN.scalarId)
            output.write(value.toInt())
        }
    }

    override fun writeBooleanArray(name: String, values: BooleanArray, force: Boolean) {
        if (force || values.isNotEmpty()) {
            writeAttributeStart(name, SimpleType.BOOLEAN.scalarId + 1)
            output.writeBE32(values.size)
            for (v in values) {
                output.write(if (v) 1 else 0)
            }
        }
    }

    override fun writeBooleanArray2D(name: String, values: List<BooleanArray>, force: Boolean) {
        if (force || values.isNotEmpty()) {
            writeAttributeStart(name, SimpleType.BOOLEAN.scalarId + 2)
            output.writeBE32(values.size)
            for (vs in values) {
                output.writeBE32(vs.size)
                for (v in vs) {
                    output.write(if (v) 1 else 0)
                }
            }
        }
    }

    override fun writeChar(name: String, value: Char, force: Boolean) {
        if (force || value != 0.toChar()) {
            writeAttributeStart(name, SimpleType.CHAR.scalarId)
            output.writeBE16(value.code)
        }
    }

    override fun writeCharArray(name: String, values: CharArray, force: Boolean) {
        if (force || values.isNotEmpty()) {
            writeAttributeStart(name, SimpleType.CHAR.scalarId + 1)
            output.writeBE32(values.size)
            for (c in values) {
                output.writeBE16(c.code)
            }
        }
    }

    override fun writeCharArray2D(name: String, values: List<CharArray>, force: Boolean) {
        if (force || values.isNotEmpty()) {
            writeAttributeStart(name, SimpleType.CHAR.scalarId + 2)
            output.writeBE32(values.size)
            for (vs in values) {
                output.writeBE32(vs.size)
                for (v in vs) {
                    output.writeBE16(v.code)
                }
            }
        }
    }


    override fun writeByte(name: String, value: Byte, force: Boolean) {
        if (force || value != 0.toByte()) {
            writeAttributeStart(name, SimpleType.BYTE.scalarId)
            output.write(value.toInt())
        }
    }

    override fun writeByteArray(name: String, values: ByteArray, force: Boolean) {
        if (force || values.isNotEmpty()) {
            writeAttributeStart(name, SimpleType.BYTE.scalarId + 1)
            output.writeBE32(values.size)
            output.write(values)
        }
    }

    override fun writeByteArray2D(name: String, values: List<ByteArray>, force: Boolean) {
        if (force || values.isNotEmpty()) {
            writeAttributeStart(name, SimpleType.BYTE.scalarId + 2)
            output.writeBE32(values.size)
            for (vs in values) {
                output.writeBE32(vs.size)
                output.write(vs)
            }
        }
    }

    override fun writeShort(name: String, value: Short, force: Boolean) {
        if (force || value != 0.toShort()) {
            writeAttributeStart(name, SimpleType.SHORT.scalarId)
            output.writeBE16(value.toInt())
        }
    }

    override fun writeShortArray(name: String, values: ShortArray, force: Boolean) {
        if (force || values.isNotEmpty()) {
            writeAttributeStart(name, SimpleType.SHORT.scalarId + 1)
            output.writeBE32(values.size)
            for (v in values) output.writeBE16(v.toInt())
        }
    }

    override fun writeShortArray2D(name: String, values: List<ShortArray>, force: Boolean) {
        if (force || values.isNotEmpty()) {
            writeAttributeStart(name, SimpleType.SHORT.scalarId + 2)
            output.writeBE32(values.size)
            for (vs in values) {
                output.writeBE32(vs.size)
                for (v in vs) output.writeBE16(v.toInt())
            }
        }
    }

    override fun writeInt(name: String, value: Int, force: Boolean) {
        if (force || value != 0) {
            writeAttributeStart(name, SimpleType.INT.scalarId)
            output.writeBE32(value)
        }
    }

    override fun writeColor(name: String, value: Int, force: Boolean) {
        writeInt(name, value, force)
    }

    override fun writeIntArray(name: String, values: IntArray, force: Boolean) {
        if (force || values.isNotEmpty()) {
            writeAttributeStart(name, SimpleType.INT.scalarId + 1)
            output.writeBE32(values.size)
            for (v in values) output.writeBE32(v)
        }
    }

    override fun writeColorArray(name: String, values: IntArray, force: Boolean) {
        writeIntArray(name, values, force)
    }

    override fun writeIntArray2D(name: String, values: List<IntArray>, force: Boolean) {
        if (force || values.isNotEmpty()) {
            writeAttributeStart(name, SimpleType.INT.scalarId + 2)
            output.writeBE32(values.size)
            for (vs in values) {
                output.writeBE32(vs.size)
                for (v in vs) output.writeBE32(v)
            }
        }
    }

    override fun writeColorArray2D(name: String, values: List<IntArray>, force: Boolean) {
        writeIntArray2D(name, values, force)
    }

    override fun writeLong(name: String, value: Long, force: Boolean) {
        if (force || value != 0L) {
            writeAttributeStart(name, SimpleType.LONG.scalarId)
            output.writeBE64(value)
        }
    }

    override fun writeLongArray(name: String, values: LongArray, force: Boolean) {
        if (force || values.isNotEmpty()) {
            writeAttributeStart(name, SimpleType.LONG.scalarId + 1)
            output.writeBE32(values.size)
            for (v in values) output.writeBE64(v)
        }
    }

    override fun writeLongArray2D(name: String, values: List<LongArray>, force: Boolean) {
        if (force || values.isNotEmpty()) {
            writeAttributeStart(name, SimpleType.LONG.scalarId + 2)
            output.writeBE32(values.size)
            for (vs in values) {
                output.writeBE32(vs.size)
                for (v in vs) output.writeBE64(v)
            }
        }
    }

    override fun writeFloat(name: String, value: Float, force: Boolean) {
        if (force || value != 0f) {
            writeAttributeStart(name, SimpleType.FLOAT.scalarId)
            output.writeBE32F(value)
        }
    }

    override fun writeFloatArray(name: String, values: FloatArray, force: Boolean) {
        if (force || values.isNotEmpty()) {
            writeAttributeStart(name, SimpleType.FLOAT.scalarId + 1)
            output.writeBE32(values.size)
            for (v in values) output.writeBE32F(v)
        }
    }

    override fun writeFloatArray2D(name: String, values: List<FloatArray>, force: Boolean) {
        if (force || values.isNotEmpty()) {
            writeAttributeStart(name, SimpleType.FLOAT.scalarId + 2)
            output.writeBE32(values.size)
            for (vs in values) {
                output.writeBE32(vs.size)
                for (v in vs) output.writeBE32F(v)
            }
        }
    }

    override fun writeDouble(name: String, value: Double, force: Boolean) {
        if (force || value != 0.0) {
            writeAttributeStart(name, SimpleType.DOUBLE.scalarId)
            output.writeBE64F(value)
        }
    }

    override fun writeDoubleArray(name: String, values: DoubleArray, force: Boolean) {
        if (force || values.isNotEmpty()) {
            writeAttributeStart(name, SimpleType.DOUBLE.scalarId + 1)
            output.writeBE32(values.size)
            for (v in values) output.writeBE64F(v)
        }
    }

    override fun writeDoubleArray2D(name: String, values: List<DoubleArray>, force: Boolean) {
        if (force || values.isNotEmpty()) {
            writeAttributeStart(name, SimpleType.DOUBLE.scalarId + 2)
            output.writeBE32(values.size)
            for (vs in values) {
                output.writeBE32(vs.size)
                for (v in vs) output.writeBE64F(v)
            }
        }
    }

    override fun writeString(name: String, value: String, force: Boolean) {
        if (force || value != "") {
            writeAttributeStart(name, SimpleType.STRING.scalarId)
            writeEfficientString(value)
        }
    }

    override fun writeStringList(name: String, values: List<String>, force: Boolean) {
        if (force || values.isNotEmpty()) {
            writeAttributeStart(name, SimpleType.STRING.scalarId + 1)
            output.writeBE32(values.size)
            for (v in values) writeEfficientString(v)
        }
    }

    override fun writeStringList2D(name: String, values: List<List<String>>, force: Boolean) {
        writeGenericList2D(name, values, force, SimpleType.STRING.scalarId + 2) {
            writeEfficientString(it)
        }
    }

    override fun writeFile(name: String, value: FileReference, force: Boolean, workspace: FileReference) {
        if (force || value != InvalidRef) {
            writeAttributeStart(name, SimpleType.REFERENCE.scalarId)
            writeEfficientString(value.toLocalPath(workspace))
        }
    }

    override fun writeFileList(name: String, values: List<FileReference>, force: Boolean, workspace: FileReference) {
        if (force || values.isNotEmpty()) {
            writeAttributeStart(name, SimpleType.REFERENCE.scalarId + 1)
            output.writeBE32(values.size)
            for (v in values) writeEfficientString(v.toLocalPath(workspace))
        }
    }

    override fun writeFileList2D(
        name: String, values: List<List<FileReference>>,
        force: Boolean, workspace: FileReference
    ) {
        writeGenericList2D(name, values, force, SimpleType.REFERENCE.scalarId + 2) { v ->
            writeEfficientString(v.toLocalPath(workspace))
        }
    }

    private fun writeVector2f(value: Vector2f) {
        output.writeBE32F(value.x)
        output.writeBE32F(value.y)
    }

    private fun writeVector3f(value: Vector3f) {
        output.writeBE32F(value.x)
        output.writeBE32F(value.y)
        output.writeBE32F(value.z)
    }

    private fun writeVector4f(value: Vector4f) {
        output.writeBE32F(value.x)
        output.writeBE32F(value.y)
        output.writeBE32F(value.z)
        output.writeBE32F(value.w)
    }

    override fun writeVector2f(name: String, value: Vector2f, force: Boolean) {
        if (force || (value.x != 0f && value.y != 0f)) {
            writeAttributeStart(name, SimpleType.VECTOR2F.scalarId)
            writeVector2f(value)
        }
    }

    override fun writeVector2fList(name: String, values: List<Vector2f>, force: Boolean) =
        writeGenericList(name, values, force, SimpleType.VECTOR2F.scalarId + 1, ::writeVector2f)

    override fun writeVector2fList2D(name: String, values: List<List<Vector2f>>, force: Boolean) =
        writeGenericList2D(name, values, force, SimpleType.VECTOR2F.scalarId + 2, ::writeVector2f)

    override fun writeVector3f(name: String, value: Vector3f, force: Boolean) {
        if (force || (value.x != 0f || value.y != 0f || value.z != 0f)) {
            writeAttributeStart(name, SimpleType.VECTOR3F.scalarId)
            writeVector3f(value)
        }
    }

    override fun writeVector3fList(name: String, values: List<Vector3f>, force: Boolean) =
        writeGenericList(name, values, force, SimpleType.VECTOR3F.scalarId + 1, ::writeVector3f)

    override fun writeVector3fList2D(name: String, values: List<List<Vector3f>>, force: Boolean) =
        writeGenericList2D(name, values, force, SimpleType.VECTOR3F.scalarId + 2, ::writeVector3f)

    override fun writeVector4f(name: String, value: Vector4f, force: Boolean) {
        if (force || (value.x != 0f || value.y != 0f || value.z != 0f || value.w != 0f)) {
            writeAttributeStart(name, SimpleType.VECTOR4F.scalarId)
            writeVector4f(value)
        }
    }

    override fun writeVector4fList(name: String, values: List<Vector4f>, force: Boolean) =
        writeGenericList(name, values, force, SimpleType.VECTOR4F.scalarId + 1, ::writeVector4f)

    override fun writeVector4fList2D(name: String, values: List<List<Vector4f>>, force: Boolean) =
        writeGenericList2D(name, values, force, SimpleType.VECTOR4F.scalarId + 2, ::writeVector4f)

    private fun writeVector2d(value: Vector2d) {
        output.writeBE64F(value.x)
        output.writeBE64F(value.y)
    }

    private fun writeVector3d(value: Vector3d) {
        output.writeBE64F(value.x)
        output.writeBE64F(value.y)
        output.writeBE64F(value.z)
    }

    private fun writeVector4d(value: Vector4d) {
        output.writeBE64F(value.x)
        output.writeBE64F(value.y)
        output.writeBE64F(value.z)
        output.writeBE64F(value.w)
    }

    override fun writeVector2d(name: String, value: Vector2d, force: Boolean) {
        if (force || (value.x != 0.0 || value.y != 0.0)) {
            writeAttributeStart(name, SimpleType.VECTOR2D.scalarId)
            writeVector2d(value)
        }
    }

    override fun writeVector2dList(name: String, values: List<Vector2d>, force: Boolean) =
        writeGenericList(name, values, force, SimpleType.VECTOR2D.scalarId + 1, ::writeVector2d)

    override fun writeVector2dList2D(name: String, values: List<List<Vector2d>>, force: Boolean) =
        writeGenericList2D(name, values, force, SimpleType.VECTOR2D.scalarId + 2, ::writeVector2d)

    override fun writeVector3d(name: String, value: Vector3d, force: Boolean) {
        if (force || (value.x != 0.0 || value.y != 0.0 || value.z != 0.0)) {
            writeAttributeStart(name, SimpleType.VECTOR3D.scalarId)
            writeVector3d(value)
        }
    }

    override fun writeVector3dList(name: String, values: List<Vector3d>, force: Boolean) =
        writeGenericList(name, values, force, SimpleType.VECTOR3D.scalarId + 1, ::writeVector3d)

    override fun writeVector3dList2D(name: String, values: List<List<Vector3d>>, force: Boolean) =
        writeGenericList2D(name, values, force, SimpleType.VECTOR3D.scalarId + 2, ::writeVector3d)

    override fun writeVector4d(name: String, value: Vector4d, force: Boolean) {
        if (force || (value.x != 0.0 || value.y != 0.0 || value.z != 0.0 || value.w != 0.0)) {
            writeAttributeStart(name, SimpleType.VECTOR4D.scalarId)
            writeVector4d(value)
        }
    }

    override fun writeVector4dList(name: String, values: List<Vector4d>, force: Boolean) =
        writeGenericList(name, values, force, SimpleType.VECTOR4D.scalarId + 1, ::writeVector4d)

    override fun writeVector4dList2D(name: String, values: List<List<Vector4d>>, force: Boolean) =
        writeGenericList2D(name, values, force, SimpleType.VECTOR4D.scalarId + 2, ::writeVector4d)

    private fun writeVector2i(value: Vector2i) {
        output.writeBE32(value.x)
        output.writeBE32(value.y)
    }

    private fun writeVector3i(value: Vector3i) {
        output.writeBE32(value.x)
        output.writeBE32(value.y)
        output.writeBE32(value.z)
    }

    private fun writeVector4i(value: Vector4i) {
        output.writeBE32(value.x)
        output.writeBE32(value.y)
        output.writeBE32(value.z)
        output.writeBE32(value.w)
    }

    override fun writeVector2i(name: String, value: Vector2i, force: Boolean) {
        if (force || (value.x != 0 || value.y != 0)) {
            writeAttributeStart(name, SimpleType.VECTOR2I.scalarId)
            writeVector2i(value)
        }
    }

    override fun writeVector3i(name: String, value: Vector3i, force: Boolean) {
        if (force || (value.x != 0 || value.y != 0 || value.z != 0)) {
            writeAttributeStart(name, SimpleType.VECTOR3I.scalarId)
            writeVector3i(value)
        }
    }

    override fun writeVector4i(name: String, value: Vector4i, force: Boolean) {
        if (force || (value.x != 0 || value.y != 0 || value.z != 0 || value.w != 0)) {
            writeAttributeStart(name, SimpleType.VECTOR4I.scalarId)
            writeVector4i(value)
        }
    }

    override fun writeVector2iList(name: String, values: List<Vector2i>, force: Boolean) =
        writeGenericList(name, values, force, SimpleType.VECTOR2I.scalarId + 1, ::writeVector2i)

    override fun writeVector3iList(name: String, values: List<Vector3i>, force: Boolean) =
        writeGenericList(name, values, force, SimpleType.VECTOR3I.scalarId + 1, ::writeVector3i)

    override fun writeVector4iList(name: String, values: List<Vector4i>, force: Boolean) =
        writeGenericList(name, values, force, SimpleType.VECTOR4I.scalarId + 1, ::writeVector4i)

    override fun writeVector2iList2D(name: String, values: List<List<Vector2i>>, force: Boolean) =
        writeGenericList2D(name, values, force, SimpleType.VECTOR2I.scalarId + 2, ::writeVector2i)

    override fun writeVector3iList2D(name: String, values: List<List<Vector3i>>, force: Boolean) =
        writeGenericList2D(name, values, force, SimpleType.VECTOR3I.scalarId + 2, ::writeVector3i)

    override fun writeVector4iList2D(name: String, values: List<List<Vector4i>>, force: Boolean) =
        writeGenericList2D(name, values, force, SimpleType.VECTOR4I.scalarId + 2, ::writeVector4i)

    private fun writeQuaternionf(value: Quaternionf) {
        output.writeBE32F(value.x)
        output.writeBE32F(value.y)
        output.writeBE32F(value.z)
        output.writeBE32F(value.w)
    }

    private fun writeQuaterniond(value: Quaterniond) {
        output.writeBE64F(value.x)
        output.writeBE64F(value.y)
        output.writeBE64F(value.z)
        output.writeBE64F(value.w)
    }

    override fun writeQuaternionf(name: String, value: Quaternionf, force: Boolean) {
        if (force || (value.x != 0f || value.y != 0f || value.z != 0f || value.w != 1f)) {
            writeAttributeStart(name, SimpleType.QUATERNIONF.scalarId)
            writeQuaternionf(value)
        }
    }

    override fun writeQuaterniond(name: String, value: Quaterniond, force: Boolean) {
        if (force || (value.x != 0.0 || value.y != 0.0 || value.z != 0.0 || value.w != 1.0)) {
            writeAttributeStart(name, SimpleType.QUATERNIOND.scalarId)
            writeQuaterniond(value)
        }
    }

    override fun writeQuaternionfList(name: String, values: List<Quaternionf>, force: Boolean) =
        writeGenericList(name, values, force, SimpleType.QUATERNIONF.scalarId + 1) { writeQuaternionf(it) }

    override fun writeQuaternionfList2D(name: String, values: List<List<Quaternionf>>, force: Boolean) =
        writeGenericList2D(name, values, force, SimpleType.QUATERNIONF.scalarId + 2, ::writeQuaternionf)

    override fun writeQuaterniondList(name: String, values: List<Quaterniond>, force: Boolean) =
        writeGenericList(name, values, force, SimpleType.QUATERNIOND.scalarId + 1, ::writeQuaterniond)

    override fun writeQuaterniondList2D(name: String, values: List<List<Quaterniond>>, force: Boolean) =
        writeGenericList2D(name, values, force, SimpleType.QUATERNIOND.scalarId + 2, ::writeQuaterniond)

    private fun writeMatrix(value: Matrix2f) {
        output.writeBE32F(value.m00)
        output.writeBE32F(value.m01)
        output.writeBE32F(value.m10)
        output.writeBE32F(value.m11)
    }

    private fun writeMatrix(value: Matrix3x2f) {
        output.writeBE32F(value.m00)
        output.writeBE32F(value.m01)
        output.writeBE32F(value.m10)
        output.writeBE32F(value.m11)
        output.writeBE32F(value.m20)
        output.writeBE32F(value.m21)
    }

    private fun writeMatrix(value: Matrix3f) {
        output.writeBE32F(value.m00)
        output.writeBE32F(value.m01)
        output.writeBE32F(value.m02)
        output.writeBE32F(value.m10)
        output.writeBE32F(value.m11)
        output.writeBE32F(value.m12)
        output.writeBE32F(value.m20)
        output.writeBE32F(value.m21)
        output.writeBE32F(value.m22)
    }

    private fun writeMatrix(value: Matrix4x3f) {
        output.writeBE32F(value.m00)
        output.writeBE32F(value.m01)
        output.writeBE32F(value.m02)
        output.writeBE32F(value.m10)
        output.writeBE32F(value.m11)
        output.writeBE32F(value.m12)
        output.writeBE32F(value.m20)
        output.writeBE32F(value.m21)
        output.writeBE32F(value.m22)
        output.writeBE32F(value.m30)
        output.writeBE32F(value.m31)
        output.writeBE32F(value.m32)
    }

    private fun writeMatrix(value: Matrix4f) {
        output.writeBE32F(value.m00)
        output.writeBE32F(value.m01)
        output.writeBE32F(value.m02)
        output.writeBE32F(value.m03)
        output.writeBE32F(value.m10)
        output.writeBE32F(value.m11)
        output.writeBE32F(value.m12)
        output.writeBE32F(value.m13)
        output.writeBE32F(value.m20)
        output.writeBE32F(value.m21)
        output.writeBE32F(value.m22)
        output.writeBE32F(value.m23)
        output.writeBE32F(value.m30)
        output.writeBE32F(value.m31)
        output.writeBE32F(value.m32)
        output.writeBE32F(value.m33)
    }

    override fun writeMatrix2x2f(name: String, value: Matrix2f, force: Boolean) {
        writeAttributeStart(name, SimpleType.MATRIX2X2F.scalarId)
        writeMatrix(value)
    }

    override fun writeMatrix3x2f(name: String, value: Matrix3x2f, force: Boolean) {
        writeAttributeStart(name, SimpleType.MATRIX3X2F.scalarId)
        writeMatrix(value)
    }

    override fun writeMatrix3x3f(name: String, value: Matrix3f, force: Boolean) {
        writeAttributeStart(name, SimpleType.MATRIX3X3F.scalarId)
        writeMatrix(value)
    }

    override fun writeMatrix4x3f(name: String, value: Matrix4x3f, force: Boolean) {
        writeAttributeStart(name, SimpleType.MATRIX4X3F.scalarId)
        writeMatrix(value)
    }

    override fun writeMatrix4x4f(name: String, value: Matrix4f, force: Boolean) {
        writeAttributeStart(name, SimpleType.MATRIX4X4F.scalarId)
        writeMatrix(value)
    }

    override fun writeMatrix2x2fList(name: String, values: List<Matrix2f>, force: Boolean) =
        writeGenericList(name, values, force, SimpleType.MATRIX2X2F.scalarId + 1) { writeMatrix(it) }

    override fun writeMatrix3x2fList(name: String, values: List<Matrix3x2f>, force: Boolean) =
        writeGenericList(name, values, force, SimpleType.MATRIX3X2F.scalarId + 1) { writeMatrix(it) }

    override fun writeMatrix3x3fList(name: String, values: List<Matrix3f>, force: Boolean) =
        writeGenericList(name, values, force, SimpleType.MATRIX3X3F.scalarId + 1) { writeMatrix(it) }

    override fun writeMatrix4x3fList(name: String, values: List<Matrix4x3f>, force: Boolean) =
        writeGenericList(name, values, force, SimpleType.MATRIX4X3F.scalarId + 1) { writeMatrix(it) }

    override fun writeMatrix4x4fList(name: String, values: List<Matrix4f>, force: Boolean) =
        writeGenericList(name, values, force, SimpleType.MATRIX4X4F.scalarId + 1) { writeMatrix(it) }

    override fun writeMatrix2x2fList2D(name: String, values: List<List<Matrix2f>>, force: Boolean) =
        writeGenericList2D(name, values, force, SimpleType.MATRIX2X2F.scalarId + 2) { writeMatrix(it) }

    override fun writeMatrix3x2fList2D(name: String, values: List<List<Matrix3x2f>>, force: Boolean) =
        writeGenericList2D(name, values, force, SimpleType.MATRIX3X2F.scalarId + 2) { writeMatrix(it) }

    override fun writeMatrix3x3fList2D(name: String, values: List<List<Matrix3f>>, force: Boolean) =
        writeGenericList2D(name, values, force, SimpleType.MATRIX3X3F.scalarId + 2) { writeMatrix(it) }

    override fun writeMatrix4x3fList2D(name: String, values: List<List<Matrix4x3f>>, force: Boolean) =
        writeGenericList2D(name, values, force, SimpleType.MATRIX4X3F.scalarId + 2) { writeMatrix(it) }

    override fun writeMatrix4x4fList2D(name: String, values: List<List<Matrix4f>>, force: Boolean) =
        writeGenericList2D(name, values, force, SimpleType.MATRIX4X4F.scalarId + 2) { writeMatrix(it) }

    private fun writeMatrix(value: Matrix2d) {
        output.writeBE64F(value.m00)
        output.writeBE64F(value.m01)
        output.writeBE64F(value.m10)
        output.writeBE64F(value.m11)
    }

    private fun writeMatrix(value: Matrix3x2d) {
        output.writeBE64F(value.m00)
        output.writeBE64F(value.m01)
        output.writeBE64F(value.m10)
        output.writeBE64F(value.m11)
        output.writeBE64F(value.m20)
        output.writeBE64F(value.m21)
    }

    private fun writeMatrix(value: Matrix3d) {
        output.writeBE64F(value.m00)
        output.writeBE64F(value.m01)
        output.writeBE64F(value.m02)
        output.writeBE64F(value.m10)
        output.writeBE64F(value.m11)
        output.writeBE64F(value.m12)
        output.writeBE64F(value.m20)
        output.writeBE64F(value.m21)
        output.writeBE64F(value.m22)
    }

    private fun writeMatrix(value: Matrix4x3d) {
        output.writeBE64F(value.m00)
        output.writeBE64F(value.m01)
        output.writeBE64F(value.m02)
        output.writeBE64F(value.m10)
        output.writeBE64F(value.m11)
        output.writeBE64F(value.m12)
        output.writeBE64F(value.m20)
        output.writeBE64F(value.m21)
        output.writeBE64F(value.m22)
        output.writeBE64F(value.m30)
        output.writeBE64F(value.m31)
        output.writeBE64F(value.m32)
    }

    private fun writeMatrix(value: Matrix4d) {
        output.writeBE64F(value.m00)
        output.writeBE64F(value.m01)
        output.writeBE64F(value.m02)
        output.writeBE64F(value.m03)
        output.writeBE64F(value.m10)
        output.writeBE64F(value.m11)
        output.writeBE64F(value.m12)
        output.writeBE64F(value.m13)
        output.writeBE64F(value.m20)
        output.writeBE64F(value.m21)
        output.writeBE64F(value.m22)
        output.writeBE64F(value.m23)
        output.writeBE64F(value.m30)
        output.writeBE64F(value.m31)
        output.writeBE64F(value.m32)
        output.writeBE64F(value.m33)
    }

    override fun writeMatrix2x2d(name: String, value: Matrix2d, force: Boolean) {
        writeAttributeStart(name, SimpleType.MATRIX2X2D.scalarId)
        writeMatrix(value)
    }

    override fun writeMatrix3x2d(name: String, value: Matrix3x2d, force: Boolean) {
        writeAttributeStart(name, SimpleType.MATRIX3X2D.scalarId)
        writeMatrix(value)
    }

    override fun writeMatrix3x3d(name: String, value: Matrix3d, force: Boolean) {
        writeAttributeStart(name, SimpleType.MATRIX3X3D.scalarId)
        writeMatrix(value)
    }

    override fun writeMatrix4x3d(name: String, value: Matrix4x3d, force: Boolean) {
        writeAttributeStart(name, SimpleType.MATRIX4X3D.scalarId)
        writeMatrix(value)
    }

    override fun writeMatrix4x4d(name: String, value: Matrix4d, force: Boolean) {
        writeAttributeStart(name, SimpleType.MATRIX4X4D.scalarId)
        writeMatrix(value)
    }

    override fun writeMatrix2x2dList(name: String, values: List<Matrix2d>, force: Boolean) =
        writeGenericList(name, values, force, SimpleType.MATRIX2X2D.scalarId + 1) { writeMatrix(it) }

    override fun writeMatrix3x2dList(name: String, values: List<Matrix3x2d>, force: Boolean) =
        writeGenericList(name, values, force, SimpleType.MATRIX3X2D.scalarId + 1) { writeMatrix(it) }

    override fun writeMatrix3x3dList(name: String, values: List<Matrix3d>, force: Boolean) =
        writeGenericList(name, values, force, SimpleType.MATRIX3X3D.scalarId + 1) { writeMatrix(it) }

    override fun writeMatrix4x3dList(name: String, values: List<Matrix4x3d>, force: Boolean) =
        writeGenericList(name, values, force, SimpleType.MATRIX4X3D.scalarId + 1) { writeMatrix(it) }

    override fun writeMatrix4x4dList(name: String, values: List<Matrix4d>, force: Boolean) =
        writeGenericList(name, values, force, SimpleType.MATRIX4X4D.scalarId + 1) { writeMatrix(it) }

    override fun writeMatrix2x2dList2D(name: String, values: List<List<Matrix2d>>, force: Boolean) =
        writeGenericList2D(name, values, force, SimpleType.MATRIX2X2D.scalarId + 2) { writeMatrix(it) }

    override fun writeMatrix3x2dList2D(name: String, values: List<List<Matrix3x2d>>, force: Boolean) =
        writeGenericList2D(name, values, force, SimpleType.MATRIX3X2D.scalarId + 2) { writeMatrix(it) }

    override fun writeMatrix3x3dList2D(name: String, values: List<List<Matrix3d>>, force: Boolean) =
        writeGenericList2D(name, values, force, SimpleType.MATRIX3X3D.scalarId + 2) { writeMatrix(it) }

    override fun writeMatrix4x3dList2D(name: String, values: List<List<Matrix4x3d>>, force: Boolean) =
        writeGenericList2D(name, values, force, SimpleType.MATRIX4X3D.scalarId + 2) { writeMatrix(it) }

    override fun writeMatrix4x4dList2D(name: String, values: List<List<Matrix4d>>, force: Boolean) =
        writeGenericList2D(name, values, force, SimpleType.MATRIX4X4D.scalarId + 2) { writeMatrix(it) }

    private fun writeAABBf(value: AABBf) {
        output.writeBE32F(value.minX)
        output.writeBE32F(value.minY)
        output.writeBE32F(value.minZ)
        output.writeBE32F(value.maxX)
        output.writeBE32F(value.maxY)
        output.writeBE32F(value.maxZ)
    }

    private fun writeAABBd(value: AABBd) {
        output.writeBE64F(value.minX)
        output.writeBE64F(value.minY)
        output.writeBE64F(value.minZ)
        output.writeBE64F(value.maxX)
        output.writeBE64F(value.maxY)
        output.writeBE64F(value.maxZ)
    }

    override fun writeAABBf(name: String, value: AABBf, force: Boolean) {
        writeAttributeStart(name, SimpleType.AABBF.scalarId)
        writeAABBf(value)
    }

    override fun writeAABBd(name: String, value: AABBd, force: Boolean) {
        writeAttributeStart(name, SimpleType.AABBD.scalarId)
        writeAABBd(value)
    }

    override fun writeAABBfList(name: String, values: List<AABBf>, force: Boolean) =
        writeGenericList(name, values, force, SimpleType.AABBF.scalarId + 1, ::writeAABBf)

    override fun writeAABBdList(name: String, values: List<AABBd>, force: Boolean) =
        writeGenericList(name, values, force, SimpleType.AABBD.scalarId + 1, ::writeAABBd)

    override fun writeAABBfList2D(name: String, values: List<List<AABBf>>, force: Boolean) =
        writeGenericList2D(name, values, force, SimpleType.AABBF.scalarId + 2, ::writeAABBf)

    override fun writeAABBdList2D(name: String, values: List<List<AABBd>>, force: Boolean) =
        writeGenericList2D(name, values, force, SimpleType.AABBD.scalarId + 2, ::writeAABBd)

    private fun writePlanef(value: Planef) {
        output.writeBE32F(value.dirX)
        output.writeBE32F(value.dirY)
        output.writeBE32F(value.dirZ)
        output.writeBE32F(value.distance)
    }

    private fun writePlaned(value: Planed) {
        output.writeBE64F(value.dirX)
        output.writeBE64F(value.dirY)
        output.writeBE64F(value.dirZ)
        output.writeBE64F(value.distance)
    }

    override fun writePlanef(name: String, value: Planef, force: Boolean) {
        writeAttributeStart(name, SimpleType.PLANEF.scalarId)
        writePlanef(value)
    }

    override fun writePlaned(name: String, value: Planed, force: Boolean) {
        writeAttributeStart(name, SimpleType.PLANED.scalarId)
        writePlaned(value)
    }

    override fun writePlanefList(name: String, values: List<Planef>, force: Boolean) =
        writeGenericList(name, values, force, SimpleType.PLANEF.scalarId + 1, ::writePlanef)

    override fun writePlanedList(name: String, values: List<Planed>, force: Boolean) =
        writeGenericList(name, values, force, SimpleType.PLANED.scalarId + 1, ::writePlaned)

    override fun writePlanefList2D(name: String, values: List<List<Planef>>, force: Boolean) =
        writeGenericList2D(name, values, force, SimpleType.PLANEF.scalarId + 2, ::writePlanef)

    override fun writePlanedList2D(name: String, values: List<List<Planed>>, force: Boolean) =
        writeGenericList2D(name, values, force, SimpleType.PLANED.scalarId + 2, ::writePlaned)

    override fun writeNull(name: String?) {
        if (name != null) writeAttributeStart(name, OBJECT_NULL)
        else output.write(OBJECT_NULL)
    }

    override fun writePointer(name: String?, className: String, ptr: Int, value: Saveable) {
        if (name != null) writeAttributeStart(name, OBJECT_PTR)
        else output.write(OBJECT_PTR)
        output.writeBE32(ptr)
    }

    private fun writeObjectEnd() {
        output.writeBE32(-2)
    }

    override fun writeObjectImpl(name: String?, value: Saveable) {
        if (name != null) writeAttributeStart(name, OBJECT_IMPL)
        else output.write(OBJECT_IMPL)
        usingType(value.className) {
            writeTypeString(currentClass)
            output.writeBE32(getPointer(value)!!)
            value.save(this)
            writeObjectEnd()
        }
    }

    private fun <V> writeGenericList(
        name: String,
        values: List<V>,
        force: Boolean,
        type: Int,
        writeInstance: (V) -> Unit
    ) {
        if (force || values.isNotEmpty()) {
            writeAttributeStart(name, type)
            output.writeBE32(values.size)
            for (index in values.indices) {
                writeInstance(values[index])
            }
        }
    }

    fun <V> writeGenericList2D(
        name: String,
        values: List<List<V>>,
        force: Boolean,
        type: Int,
        writeInstance: (V) -> Unit
    ) {
        if (force || values.isNotEmpty()) {
            writeAttributeStart(name, type)
            output.writeBE32(values.size)
            for (i in values.indices) {
                val vs = values[i]
                output.writeBE32(vs.size)
                for (j in vs.indices) writeInstance(vs[j])
            }
        }
    }

    override fun <V : Saveable> writeObjectList(self: Saveable?, name: String, values: List<V>, force: Boolean) {
        if (force || values.isNotEmpty()) {
            if (values.isNotEmpty()) {
                val firstType = values.first().className
                val allSameType = values.all { it.className == firstType }
                if (allSameType) {
                    writeHomogenousObjectList(self, name, values, force)
                } else {
                    writeGenericList(name, values, force, OBJECT_ARRAY) {
                        writeObject(null, null, it, true)
                    }
                }
            } else {
                writeAttributeStart(name, OBJECT_ARRAY)
                output.writeBE32(0)
            }
        }
    }

    override fun <V : Saveable?> writeNullableObjectList(
        self: Saveable?, name: String,
        values: List<V>, force: Boolean
    ) {
        if (force || values.isNotEmpty()) {
            if (values.isNotEmpty()) {
                val firstType = values.first()?.className
                val allSameType = values.all { it?.className == firstType }
                if (firstType != null && allSameType) {
                    writeHomogenousObjectList(self, name, values, force)
                } else {
                    writeGenericList(name, values, force, OBJECT_ARRAY) {
                        writeObject(null, null, it, true)
                    }
                }
            } else {
                writeAttributeStart(name, OBJECT_ARRAY)
                output.writeBE32(0)
            }
        }
    }

    override fun <V : Saveable> writeObjectList2D(
        self: Saveable?,
        name: String,
        values: List<List<V>>,
        force: Boolean
    ) {
        writeGenericList(name, values, force, OBJECT_ARRAY_2D) {
            output.writeBE32(it.size)
            for (i in it.indices) {
                writeObject(null, null, it[i], true)
            }
        }
    }

    override fun <V : Saveable?> writeHomogenousObjectList(
        self: Saveable?, name: String,
        values: List<V>, force: Boolean
    ) {
        if (force || values.isNotEmpty()) {
            writeAttributeStart(name, OBJECTS_HOMOGENOUS_ARRAY)
            writeTypeString(values.firstOrNull()?.className ?: "")
            output.writeBE32(values.size)
            for (element in values) {
                element!!.save(this)
                writeObjectEnd()
            }
        }
    }

    override fun writeListStart() {
        writeAttributeStart("", OBJECT_LIST_UNKNOWN_LENGTH)
    }

    override fun writeListEnd() {
        output.write(LIST_END)
    }

    override fun writeListSeparator() {
        output.write(LIST_SEPARATOR)
    }

    override fun flush() {
        output.flush()
    }

    override fun close() {
        output.close()
    }

    companion object {
        const val LIST_SEPARATOR = 17
        const val LIST_END = 37
    }
}