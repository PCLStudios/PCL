import com.pcl.chronoslock.Wake
fun main() {
    val yes = listOf("chronos lock", "kronos luck", "cronus clock", "chrono slack", "chronoslock", "chronos", "hey chronos lock combat mode",
        "crono lock", "kronos look", "chrono s lock", "chronus lark", "chronos block", "kron os lock", "chrono's lock")
    val no = listOf("lock the door", "combat mode", "what time is it", "i like clocks", "chronic pain is bad", "open the lock screen", "good night", "hello there", "turn on the flashlight")
    for (s in yes) println("YES? %-32s -> %d".format(s, Wake.find(s.split(" "), false)))
    for (s in no) println("NO?  %-32s -> %d".format(s, Wake.find(s.split(" "), false)))
}
