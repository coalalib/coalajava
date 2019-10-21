package com.ndmsystems.coala

import java.io.File

/**
 * Created by Toukhvatullin Marat on 21.10.2019.
 */
enum class DummyData(private val fileName: String) {
    OptionsAllPossible("coapMessages/CoAPMessageDummyOptionsAllPossible.bin");

    fun read(): ByteArray {
        /*println("1:${File(
                javaClass.classLoader
                        ?.getResource(fileName)
                        ?.file
        ).readBytes().contentToString()}, path:$fileName")*/
        return File(
                javaClass.classLoader
                        ?.getResource(fileName)
                        ?.file
        ).readBytes()
    }
}