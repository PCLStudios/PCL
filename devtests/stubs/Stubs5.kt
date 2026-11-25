package android.net
class Uri { companion object { fun parse(s: String) = Uri(); fun encode(s: String) = s } }
class Network
class NetworkCapabilities { fun hasTransport(t: Int) = false; companion object { const val TRANSPORT_WIFI = 1; const val TRANSPORT_CELLULAR = 0 } }
class ConnectivityManager { val activeNetwork: Network? = null; fun getNetworkCapabilities(n: Network?): NetworkCapabilities? = null }
