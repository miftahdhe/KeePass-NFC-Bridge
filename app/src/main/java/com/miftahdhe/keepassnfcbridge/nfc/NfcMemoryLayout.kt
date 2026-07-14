package com.miftahdhe.keepassnfcbridge.nfc

data class NfcMemoryLayout(

    val name: String,

    val firstWritablePage: Int,

    val lastWritablePage: Int,

    val userBytes: Int,

    val totalPages: Int,

    val lockPageStart: Int,

    val configPageStart: Int

)

object NfcLayouts {

    val UNKNOWN = NfcMemoryLayout(

        name = "Unknown",

        firstWritablePage = 0,

        lastWritablePage = 0,

        userBytes = 0,

        totalPages = 0,

        lockPageStart = 0,

        configPageStart = 0

    )

    val ULTRALIGHT = NfcMemoryLayout(

        name = "MIFARE Ultralight",

        firstWritablePage = 4,

        lastWritablePage = 15,

        userBytes = 48,

        totalPages = 16,

        lockPageStart = 2,

        configPageStart = 16

    )

    val NTAG213 = NfcMemoryLayout(

        name = "NTAG213",

        firstWritablePage = 4,

        lastWritablePage = 39,

        userBytes = 144,

        totalPages = 45,

        lockPageStart = 2,

        configPageStart = 40

    )

    val NTAG215 = NfcMemoryLayout(

        name = "NTAG215",

        firstWritablePage = 4,

        lastWritablePage = 129,

        userBytes = 504,

        totalPages = 135,

        lockPageStart = 2,

        configPageStart = 130

    )

    val NTAG424_DNA = NfcMemoryLayout(
        name = "NTAG424 DNA",
        firstWritablePage = 4,
        lastWritablePage = 0x40,
        userBytes = 256,
        totalPages = 0x45,
        lockPageStart = 0,
        configPageStart = 0
    )
    val NTAG424_DNA_EV2 = NfcMemoryLayout(
        name = "NTAG424 DNA EV2",
        firstWritablePage = 4,
        lastWritablePage = 0x40,
        userBytes = 256,
        totalPages = 0x45,
        lockPageStart = 0,
        configPageStart = 0
    )
    val NTAG424_DNA_EV3 = NfcMemoryLayout(
        name = "NTAG424 DNA EV3",
        firstWritablePage = 4,
        lastWritablePage = 0x40,
        userBytes = 256,
        totalPages = 0x45,
        lockPageStart = 0,
        configPageStart = 0
    )
    val NTAG216 = NfcMemoryLayout(

        name = "NTAG216",

        firstWritablePage = 4,

        lastWritablePage = 225,

        userBytes = 888,

        totalPages = 231,

        lockPageStart = 2,

        configPageStart = 226

    )

}

object NfcMemoryDetector {

    fun detect(
        userBytes: Int
    ): NfcMemoryLayout {

        return when (userBytes) {

            48 ->
                NfcLayouts.ULTRALIGHT

            144 ->
                NfcLayouts.NTAG213

            504 ->
                NfcLayouts.NTAG215

            888 ->
                NfcLayouts.NTAG216

            else ->
                NfcLayouts.UNKNOWN

        }

    }

}