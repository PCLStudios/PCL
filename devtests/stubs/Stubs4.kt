package android.media
class AudioManager { fun adjustStreamVolume(s: Int, d: Int, f: Int) {}; fun setStreamVolume(s: Int, v: Int, f: Int) {}; fun getStreamMaxVolume(s: Int) = 15; fun dispatchMediaKeyEvent(e: android.view.KeyEvent) {}
  companion object { const val STREAM_MUSIC = 3; const val ADJUST_RAISE = 1; const val ADJUST_LOWER = -1; const val ADJUST_MUTE = -100; const val ADJUST_UNMUTE = 100 } }
