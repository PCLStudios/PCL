package android.hardware.camera2
class CameraCharacteristics { fun <T> get(k: Key<T>): T? = null; class Key<T>; companion object { val FLASH_INFO_AVAILABLE = Key<Boolean>() } }
class CameraManager { val cameraIdList: Array<String> = arrayOf(); fun getCameraCharacteristics(id: String) = CameraCharacteristics(); fun setTorchMode(id: String, on: Boolean) {} }
