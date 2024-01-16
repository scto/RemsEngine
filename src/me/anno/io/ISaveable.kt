package me.anno.io

import me.anno.Build
import me.anno.ecs.annotations.Type
import me.anno.ecs.prefab.PrefabSaveable
import me.anno.io.base.BaseWriter
import me.anno.io.base.UnknownClassException
import me.anno.io.files.FileReference
import me.anno.engine.inspector.CachedReflections
import me.anno.utils.OS
import me.anno.utils.structures.lists.Lists.firstOrNull2
import org.apache.logging.log4j.LogManager
import org.joml.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.reflect.KClass

/**
 * interface for everything that should be saveable;
 * please use Saveable.class, if possible
 * */
interface ISaveable {

    /**
     * class id for saving instances of this class
     * needs to be unique for that class, and needs to be registered
     * */
    val className: String

    /**
     * a guess, small objects shouldn't contain large ones
     * (e.g., human containing building vs building containing human)
     * */
    val approxSize: Int

    /**
     * write all data, which needs to be recovered, to the writer
     * */
    fun save(writer: BaseWriter)

    fun onReadingStarted() {}
    fun onReadingEnded() {}

    fun readBoolean(name: String, value: Boolean)
    fun readBooleanArray(name: String, values: BooleanArray)
    fun readBooleanArray2D(name: String, values: Array<BooleanArray>)

    fun readByte(name: String, value: Byte)
    fun readByteArray(name: String, values: ByteArray)
    fun readByteArray2D(name: String, values: Array<ByteArray>)

    fun readChar(name: String, value: Char)
    fun readCharArray(name: String, values: CharArray)
    fun readCharArray2D(name: String, values: Array<CharArray>)

    fun readShort(name: String, value: Short)
    fun readShortArray(name: String, values: ShortArray)
    fun readShortArray2D(name: String, values: Array<ShortArray>)

    fun readInt(name: String, value: Int)
    fun readIntArray(name: String, values: IntArray)
    fun readIntArray2D(name: String, values: Array<IntArray>)

    fun readLong(name: String, value: Long)
    fun readLongArray(name: String, values: LongArray)
    fun readLongArray2D(name: String, values: Array<LongArray>)

    fun readFloat(name: String, value: Float)
    fun readFloatArray(name: String, values: FloatArray)
    fun readFloatArray2D(name: String, values: Array<FloatArray>)

    fun readDouble(name: String, value: Double)
    fun readDoubleArray(name: String, values: DoubleArray)
    fun readDoubleArray2D(name: String, values: Array<DoubleArray>)

    fun readString(name: String, value: String)
    fun readStringArray(name: String, values: Array<String>)
    fun readStringArray2D(name: String, values: Array<Array<String>>)

    fun readFile(name: String, value: FileReference)
    fun readFileArray(name: String, values: Array<FileReference>)
    fun readFileArray2D(name: String, values: Array<Array<FileReference>>)

    fun readObject(name: String, value: ISaveable?)
    fun readObjectArray(name: String, values: Array<ISaveable?>)
    fun readObjectArray2D(name: String, values: Array<Array<ISaveable?>>)

    fun readVector2f(name: String, value: Vector2f)
    fun readVector2fArray(name: String, values: Array<Vector2f>)
    fun readVector2fArray2D(name: String, values: Array<Array<Vector2f>>)

    fun readVector3f(name: String, value: Vector3f)
    fun readVector3fArray(name: String, values: Array<Vector3f>)
    fun readVector3fArray2D(name: String, values: Array<Array<Vector3f>>)

    fun readVector4f(name: String, value: Vector4f)
    fun readVector4fArray(name: String, values: Array<Vector4f>)
    fun readVector4fArray2D(name: String, values: Array<Array<Vector4f>>)

    fun readVector2d(name: String, value: Vector2d)
    fun readVector2dArray(name: String, values: Array<Vector2d>)
    fun readVector2dArray2D(name: String, values: Array<Array<Vector2d>>)

    fun readVector3d(name: String, value: Vector3d)
    fun readVector3dArray(name: String, values: Array<Vector3d>)
    fun readVector3dArray2D(name: String, values: Array<Array<Vector3d>>)

    fun readVector4d(name: String, value: Vector4d)
    fun readVector4dArray(name: String, values: Array<Vector4d>)
    fun readVector4dArray2D(name: String, values: Array<Array<Vector4d>>)

    fun readVector2i(name: String, value: Vector2i)
    fun readVector2iArray(name: String, values: Array<Vector2i>)
    fun readVector2iArray2D(name: String, values: Array<Array<Vector2i>>)

    fun readVector3i(name: String, value: Vector3i)
    fun readVector3iArray(name: String, values: Array<Vector3i>)
    fun readVector3iArray2D(name: String, values: Array<Array<Vector3i>>)

    fun readVector4i(name: String, value: Vector4i)
    fun readVector4iArray(name: String, values: Array<Vector4i>)
    fun readVector4iArray2D(name: String, values: Array<Array<Vector4i>>)


    // read matrices
    fun readMatrix2x2f(name: String, value: Matrix2f)
    fun readMatrix3x2f(name: String, value: Matrix3x2f)
    fun readMatrix3x3f(name: String, value: Matrix3f)
    fun readMatrix4x3f(name: String, value: Matrix4x3f)
    fun readMatrix4x4f(name: String, value: Matrix4f)
    fun readMatrix2x2fArray(name: String, values: Array<Matrix2f>)
    fun readMatrix3x2fArray(name: String, values: Array<Matrix3x2f>)
    fun readMatrix3x3fArray(name: String, values: Array<Matrix3f>)
    fun readMatrix4x3fArray(name: String, values: Array<Matrix4x3f>)
    fun readMatrix4x4fArray(name: String, values: Array<Matrix4f>)
    fun readMatrix2x2fArray2D(name: String, values: Array<Array<Matrix2f>>)
    fun readMatrix3x2fArray2D(name: String, values: Array<Array<Matrix3x2f>>)
    fun readMatrix3x3fArray2D(name: String, values: Array<Array<Matrix3f>>)
    fun readMatrix4x3fArray2D(name: String, values: Array<Array<Matrix4x3f>>)
    fun readMatrix4x4fArray2D(name: String, values: Array<Array<Matrix4f>>)

    fun readMatrix2x2d(name: String, value: Matrix2d)
    fun readMatrix3x2d(name: String, value: Matrix3x2d)
    fun readMatrix3x3d(name: String, value: Matrix3d)
    fun readMatrix4x3d(name: String, value: Matrix4x3d)
    fun readMatrix4x4d(name: String, value: Matrix4d)
    fun readMatrix2x2dArray(name: String, values: Array<Matrix2d>)
    fun readMatrix3x2dArray(name: String, values: Array<Matrix3x2d>)
    fun readMatrix3x3dArray(name: String, values: Array<Matrix3d>)
    fun readMatrix4x3dArray(name: String, values: Array<Matrix4x3d>)
    fun readMatrix4x4dArray(name: String, values: Array<Matrix4d>)
    fun readMatrix2x2dArray2D(name: String, values: Array<Array<Matrix2d>>)
    fun readMatrix3x2dArray2D(name: String, values: Array<Array<Matrix3x2d>>)
    fun readMatrix3x3dArray2D(name: String, values: Array<Array<Matrix3d>>)
    fun readMatrix4x3dArray2D(name: String, values: Array<Array<Matrix4x3d>>)
    fun readMatrix4x4dArray2D(name: String, values: Array<Array<Matrix4d>>)

    // quaternions
    fun readQuaternionf(name: String, value: Quaternionf)
    fun readQuaternionfArray(name: String, values: Array<Quaternionf>)
    fun readQuaternionfArray2D(name: String, values: Array<Array<Quaternionf>>)
    fun readQuaterniond(name: String, value: Quaterniond)
    fun readQuaterniondArray(name: String, values: Array<Quaterniond>)
    fun readQuaterniondArray2D(name: String, values: Array<Array<Quaterniond>>)

    // aabbs
    fun readAABBf(name: String, value: AABBf)
    fun readAABBfArray(name: String, values: Array<AABBf>)
    fun readAABBfArray2D(name: String, values: Array<Array<AABBf>>)
    fun readAABBd(name: String, value: AABBd)
    fun readAABBdArray(name: String, values: Array<AABBd>)
    fun readAABBdArray2D(name: String, values: Array<Array<AABBd>>)

    // planes
    fun readPlanef(name: String, value: Planef)
    fun readPlanefArray(name: String, values: Array<Planef>)
    fun readPlanefArray2D(name: String, values: Array<Array<Planef>>)
    fun readPlaned(name: String, value: Planed)
    fun readPlanedArray(name: String, values: Array<Planed>)
    fun readPlanedArray2D(name: String, values: Array<Array<Planed>>)

    fun readMap(name: String, value: Map<Any?, Any?>)

    /**
     * can saving be ignored?, because this is default anyway?
     * */
    fun isDefaultValue(): Boolean

    /**
     * tries to insert value into all properties with matching name
     * returns true on success
     * */
    fun readSerializableProperty(name: String, value: Any?): Boolean {
        val reflections = getReflections()
        return reflections.set(this, name, value)
    }

    fun saveSerializableProperties(writer: BaseWriter) {
        val reflections = getReflections()
        for ((name, field) in reflections.allProperties) {
            if (field.serialize) {
                val value = field.getter(this)
                val type = (field.annotations.firstOrNull2 { it is Type } as? Type)?.type
                if (type != null) {
                    writer.writeSomething(this, type, name, value, field.forceSaving ?: (value is Boolean))
                } else {
                    writer.writeSomething(this, name, value, field.forceSaving ?: (value is Boolean))
                }
            }
        }
    }

    fun getReflections(): CachedReflections {
        val clazz = this::class
        return reflectionCache.getOrPut(clazz) { CachedReflections(clazz) }
    }

    operator fun get(propertyName: String): Any? {
        return getReflections()[this, propertyName]
    }

    operator fun set(propertyName: String, value: Any?): Boolean {
        val reflections = getReflections()
        return reflections.set(this, propertyName, value)
    }

    companion object {

        private val LOGGER = LogManager.getLogger(ISaveable::class)
        private val reflectionCache: MutableMap<KClass<*>, CachedReflections> =
            if (OS.isWeb) HashMap()
            else ConcurrentHashMap()

        fun getReflections(instance: Any): CachedReflections {
            val clazz = instance::class
            return reflectionCache.getOrPut(clazz) { CachedReflections(clazz) }
        }

        fun get(instance: Any, name: String): Any? {
            return getReflections(instance)[instance, name]
        }

        fun set(instance: Any, name: String, value: Any?): Boolean {
            val reflections = getReflections(instance)
            return reflections.set(instance, name, value)
        }

        class RegistryEntry(
            val sampleInstance: ISaveable,
            private val generator: (() -> ISaveable)? = null
        ) {
            private val clazz = sampleInstance.javaClass
            fun generate(): ISaveable = generator?.invoke() ?: clazz.newInstance()
        }

        fun createOrNull(type: String): ISaveable? {
            return objectTypeRegistry[type]?.generate()
        }

        fun create(type: String): ISaveable {
            return objectTypeRegistry[type]?.generate() ?: throw UnknownClassException(type)
        }

        fun getSample(type: String) = objectTypeRegistry[type]?.sampleInstance

        fun getClass(type: String): Class<out ISaveable>? {
            val instance = objectTypeRegistry[type]?.sampleInstance ?: return superTypeRegistry[type]
            return instance.javaClass
        }

        fun getByClass(clazz: KClass<*>): RegistryEntry? {
            return objectTypeByClass[clazz]
        }

        fun getByClass(clazz: Class<*>): RegistryEntry? {
            return objectTypeByClass[clazz]
        }

        fun <V : ISaveable> getInstanceOf(clazz: KClass<V>): Map<String, RegistryEntry> {
            return objectTypeRegistry.filterValues { clazz.isInstance(it.sampleInstance) }
        }

        val objectTypeRegistry = HashMap<String, RegistryEntry>()
        private val objectTypeByClass = HashMap<Any, RegistryEntry>()
        private val superTypeRegistry = HashMap<String, Class<out ISaveable>>()

        fun checkInstance(instance0: ISaveable) {
            if (Build.isDebug && instance0 is PrefabSaveable) {
                val clone = try {
                    instance0.clone()
                } catch (ignored: Exception) {
                    null
                }
                if (clone != null && clone::class != instance0::class) {
                    throw RuntimeException("${instance0::class}.clone() is incorrect, returns ${clone::class} instead")
                }
            }
        }

        fun registerSuperClasses(clazz0: Class<out ISaveable>) {
            var clazz = clazz0
            while (true) {
                superTypeRegistry[clazz.simpleName] = clazz
                @Suppress("unchecked_cast")
                clazz = (clazz.superclass ?: break) as Class<out ISaveable>
            }
        }

        @JvmStatic
        fun registerCustomClass(className: String, constructor: () -> ISaveable): RegistryEntry {
            val instance0 = constructor()
            checkInstance(instance0)
            return register(className, RegistryEntry(instance0, constructor))
        }

        @JvmStatic
        fun registerCustomClass(sample: ISaveable): RegistryEntry {
            checkInstance(sample)
            val className = sample.className
            return if (sample is PrefabSaveable) {
                register(className, RegistryEntry(sample) { sample.clone() })
            } else {
                register(className, RegistryEntry(sample))
            }
        }

        @JvmStatic
        fun registerCustomClass(constructor: () -> ISaveable): RegistryEntry {
            val instance0 = constructor()
            val className = instance0.className
            val entry = register(className, RegistryEntry(instance0, constructor))
            // dangerous to be done after
            // but this allows us to skip the full implementation of clone() everywhere
            checkInstance(instance0)
            return entry
        }

        @JvmStatic
        fun <V : ISaveable> registerCustomClass(clazz: Class<V>): RegistryEntry {
            val constructor = clazz.getConstructor()
            val sample = try {
                constructor.newInstance()
            } catch (e: InstantiationException) {
                throw IllegalArgumentException("$clazz is missing constructor without parameters", e)
            }
            checkInstance(sample)
            return register(sample.className, RegistryEntry(sample))
        }

        @JvmStatic
        fun <V : ISaveable> registerCustomClass(clazz: KClass<V>): RegistryEntry {
            return registerCustomClass(clazz.java)
        }

        @JvmStatic
        fun registerCustomClass(className: String, clazz: Class<ISaveable>): RegistryEntry {
            val constructor = clazz.getConstructor()
            val sample = constructor.newInstance()
            checkInstance(sample)
            return register(className, RegistryEntry(sample))
        }

        private fun register(className: String, entry: RegistryEntry): RegistryEntry {
            val clazz = entry.sampleInstance::class
            val oldInstance = objectTypeRegistry[className]?.sampleInstance
            if (oldInstance != null && oldInstance::class != clazz) {
                LOGGER.warn(
                    "Overriding registered class {} from type {} with {}",
                    className, oldInstance::class, clazz
                )
            }
            LOGGER.info("Registering {}", className)
            objectTypeRegistry[className] = entry
            objectTypeByClass[clazz] = entry
            objectTypeByClass[clazz.java] = entry
            registerSuperClasses(entry.sampleInstance.javaClass)
            return entry
        }

    }

}