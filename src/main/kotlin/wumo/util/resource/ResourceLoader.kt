package wumo.util.resource

import java.io.*
import java.net.URL
import java.util.ArrayList

/**
 * A location from which resources can be loaded
 *
 * @author kevin
 */
interface ResourceLocation {
  
  /**
   * Get a resource as an input stream
   *
   * @param ref The reference to the resource to retrieve
   * @return A stream from which the resource can be read or
   * null if the resource can't be found in this location
   */
  fun getResourceAsStream(ref: String): InputStream?
  
  /**
   * Get a resource as a URL
   *
   * @param ref The reference to the resource to retrieve
   * @return A URL from which the resource can be read
   */
  fun getResource(ref: String): URL?
}

/**
 * A resource loading location that searches somewhere on the classpath
 *
 * @author kevin
 */
class FileSystemLocation(private val root: File) : ResourceLocation {
  
  override fun getResource(ref: String): URL? {
    return try {
      var file = File(root, ref)
      if (!file.exists()) {
        file = File(ref)
      }
      if (!file.exists()) {
        null
      } else file.toURI().toURL()
      
    } catch (e: IOException) {
      null
    }
    
  }
  
  override fun getResourceAsStream(ref: String): InputStream? {
    return try {
      var file = File(root, ref)
      if (!file.exists()) {
        file = File(ref)
      }
      FileInputStream(file)
    } catch (e: IOException) {
      null
    }
    
  }
  
}

class ClasspathLocation : ResourceLocation {
  override fun getResource(ref: String): URL {
    val cpRef = ref.replace('\\', '/')
    return ResourceLoader::class.java.classLoader.getResource(cpRef)
  }
  
  override fun getResourceAsStream(ref: String): InputStream {
    val cpRef = ref.replace('\\', '/')
    return ResourceLoader::class.java.classLoader.getResourceAsStream(cpRef)
  }
}

object ResourceLoader {
  /** The list of locations to be searched  */
  private val locations = ArrayList<ResourceLocation>()
  
  init {
    locations.add(ClasspathLocation())
    locations.add(FileSystemLocation(File(".")))
  }
  
  fun addResourceLocation(location: ResourceLocation) {
    locations.add(location)
  }
  
  fun removeResourceLocation(location: ResourceLocation) {
    locations.remove(location)
  }
  
  fun removeAllResourceLocations() {
    locations.clear()
  }
  
  fun getResourceAsStream(ref: String): InputStream {
    var `in`: InputStream? = null
    for (i in locations.indices) {
      val location = locations[i]
      `in` = location.getResourceAsStream(ref)
      if (`in` != null) {
        break
      }
    }
    
    if (`in` == null) {
      throw RuntimeException("Resource not found: $ref")
    }
    
    return BufferedInputStream(`in`)
  }
  
  /**
   * Check if a resource is available from any given resource loader
   *
   * @param ref A reference to the resource that should be checked
   * @return True if the resource can be located
   */
  fun resourceExists(ref: String): Boolean {
    var url: URL?
  
    for (i in locations.indices) {
      val location = locations[i]
      url = location.getResource(ref)
      if (url != null) {
        return true
      }
    }
    
    return false
  }
  
  /**
   * Get a resource as a URL
   *
   * @param ref The reference to the resource to retrieve
   * @return A URL from which the resource can be read
   */
  fun getResource(ref: String): URL {
    
    var url: URL? = null
    
    for (i in locations.indices) {
      val location = locations[i]
      url = location.getResource(ref)
      if (url != null) {
        break
      }
    }
    
    if (url == null) {
      throw RuntimeException("Resource not found: $ref")
    }
    
    return url
  }
}
