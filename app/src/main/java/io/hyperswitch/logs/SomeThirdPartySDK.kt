package io.hyperswitch.logs

class SomeThirdPartySDK {
    constructor() {
        println("Faulty SDK")
    }

    fun initialise() {
        throw Exception("Faulty SDK Fail")
    }

    fun mockFunction() {
        val log1 = LogBuilder().logType(LogType.INFO).category(LogCategory.API)
            .eventName(EventName.HYPER_OTA_INIT).value("FATAL ERROR FROM SDK")
            .firstEvent(true).build()


        HyperLogManager.addLog(log1)
        HyperLogManager.addLog(log1)

//        val largeList = mutableListOf<String>()
//        while (true) {
//            largeList.add("Filling memory")
//        }
//        throw Exception("Faulty SDK Fail")

    }
}