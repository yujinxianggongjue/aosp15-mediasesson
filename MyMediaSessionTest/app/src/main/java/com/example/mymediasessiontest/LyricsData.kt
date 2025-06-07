package com.example.mymediasessiontest

data class LyricsLine(
    val timeMs: Long,
    val text: String
)

data class LyricsData(
    val lines: List<LyricsLine> = emptyList(),
    val hasLyrics: Boolean = false
) {
    fun getCurrentLine(currentPositionMs: Long): LyricsLine? {
        if (lines.isEmpty()) return null
        
        // 找到当前时间对应的歌词行
        var currentLine: LyricsLine? = null
        for (line in lines) {
            if (line.timeMs <= currentPositionMs) {
                currentLine = line
            } else {
                break
            }
        }
        return currentLine
    }
    
    fun getNextLine(currentPositionMs: Long): LyricsLine? {
        if (lines.isEmpty()) return null
        
        // 找到下一行歌词
        for (line in lines) {
            if (line.timeMs > currentPositionMs) {
                return line
            }
        }
        return null
    }
    
    companion object {
        // 从LRC格式解析歌词
        fun parseLrcContent(lrcContent: String): LyricsData {
            if (lrcContent.isBlank()) return LyricsData(emptyList(), false)
            
            val lines = mutableListOf<LyricsLine>()
            val regex = "\\[(\\d{2}):(\\d{2})\\.(\\d{2})\\](.*)".toRegex()
            
            lrcContent.lines().forEach { line ->
                val matchResult = regex.find(line)
                if (matchResult != null) {
                    val (minutes, seconds, hundredths, text) = matchResult.destructured
                    val timeMs = (minutes.toLong() * 60 * 1000) + 
                                 (seconds.toLong() * 1000) + 
                                 (hundredths.toLong() * 10)
                    lines.add(LyricsLine(timeMs, text.trim()))
                }
            }
            
            return LyricsData(lines.sortedBy { it.timeMs }, lines.isNotEmpty())
        }
    }
}