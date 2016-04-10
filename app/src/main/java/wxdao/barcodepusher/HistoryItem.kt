package wxdao.barcodepusher

data class HistoryItem(
        var remote: String = "",
        var content: String = "",
        var uuid: String = "",
        var timestamp: Long = 0
)