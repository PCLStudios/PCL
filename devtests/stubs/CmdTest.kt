import com.pcl.chronoslock.*
fun main() {
    val tests = listOf("combat mode" to "combat mode", "disengage combat mode" to "disengage combat mode", "red alert" to "red alert", "quantum focus" to "quantum focus",
        "whisper mode" to "whisper mode", "stop whispering" to "stop whispering  /  normal mode", "open third eye" to "open third eye", "close third eye" to "close third eye",
        "ask what is gravity" to "ask <anything>", "flashlight on" to "flashlight on", "turn off the flashlight" to "flashlight off",
        "what time is it" to "what time is it", "what is the time left on the timer" to "how much time is left", "set a timer for five minutes" to "set a timer for five minutes",
        "cancel the timer" to "cancel timer", "remind me in ten minutes to call mom" to "remind me in ten minutes to <task>", "set an alarm for seven thirty am" to "set an alarm for seven thirty a m",
        "take a note buy milk" to "take a note <anything>", "read my notes" to "read my notes", "what is five plus three" to "what is five plus three",
        "battery" to "battery status", "network status" to "network status", "status report" to "status report", "pause the music" to "pause music  /  stop music", "next song" to "next song  /  skip",
        "play music" to "play music  /  resume", "set volume to fifty percent" to "set volume to fifty percent", "volume up" to "volume up  /  louder", "mute" to "mute", "unmute" to "unmute",
        "open spotify" to "open <any app name>", "open camera" to "open camera", "open voice settings" to "open voice settings", "show commands" to "show commands  /  help",
        "tell me a joke" to "tell me a joke", "say hello world" to "say <anything>", "repeat that" to "repeat that", "spell banana" to "spell <word>", "good night" to "good night",
        "disengage" to "disengage", "engage" to "engage", "stand down" to "stand down  /  all clear", "hello" to "hello  /  thank you", "who are you" to "who are you",
        "open maps" to "open maps", "search for pizza near me" to "search for  <anything>", "what is the date" to "what is the date", "start stopwatch" to "start stopwatch  /  stop stopwatch",
        "good morning" to "good morning / afternoon / evening", "clear my notes" to "clear notes", "speak faster" to "speak faster", "quiet" to "quiet  /  silence", "tell me a fact" to "tell me a fact")
    var bad = 0
    for ((s, want) in tests) { val got = Commands.match(s)?.phrase; if (got != want) { bad++; println("MISMATCH '$s' -> '$got' (wanted '$want')") } }
    println("${tests.size - bad}/${tests.size} routed correctly; ${Commands.all.size} commands")
}
