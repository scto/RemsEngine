package me.anno.io

import me.anno.Engine
import me.anno.utils.structures.arrays.ExpandingByteArray
import me.anno.utils.types.Strings.isBlank2
import org.apache.logging.log4j.LogManager
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import kotlin.concurrent.thread

@Suppress("unused")
open class CommandLineReader {

    companion object {
        @JvmStatic
        private val LOGGER = LogManager.getLogger(CommandLineReader::class)
    }

    class TimeoutReader(val input: InputStream) : Closeable {

        val str = ExpandingByteArray(64)

        fun readLine(): String? {
            while (!Engine.shutdown) {
                if (isReady()) {
                    when (val char = input.read()) {
                        '\r'.code -> continue // skip it
                        '\n'.code -> {// return line
                            return if (str.size > 0) {
                                val value = str.array!!.decodeToString( 0, str.size)
                                str.clear()
                                value
                            } else ""
                        }
                        -1 -> break // eof
                        else -> str.add(char.toByte())
                    }
                } else Thread.sleep(1)
            }
            return null
        }

        override fun close() {
            input.close()
        }

        private fun isReady(): Boolean {
            return try {
                this.input.available() > 0
            } catch (var2: IOException) {
                false
            }
        }
    }

    fun start() {
        thread(name = "CommandReader") {
            val input = TimeoutReader(System.`in`)
            while (true) {
                val line = input.readLine() ?: break
                if (line.isBlank2()) continue
                interpret(line)
            }
        }
    }

    open fun interpret(line: String) {
        LOGGER.info(line)
    }
}