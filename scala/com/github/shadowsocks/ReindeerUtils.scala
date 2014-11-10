
package com.github.shadowsocks

import android.content.{Intent, SharedPreferences, Context}
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.{BitmapDrawable, Drawable}
import android.util.Log
import java.io._
import java.net.{UnknownHostException, InetAddress, NetworkInterface}
import org.apache.http.conn.util.InetAddressUtils
import scala.collection.mutable.ArrayBuffer
//import org.xbill.DNS._

import scala.Some
import android.graphics.{Canvas, Bitmap}
import android.app.ActivityManager
import android.os.Build
import android.provider.Settings

import android.content.res.AssetManager

object ReindeerUtils {

  val TAG: String = "Shadowsocks"
  val ABI_PROP: String = "ro.product.cpu.abi"
//  val ABI2_PROP: String = "ro.product.cpu.abi2"
  val ARM_ABI: String = "arm"
  val X86_ABI: String = "x86"
//  val MIPS_ABI: String = "mips"
  val DEFAULT_SHELL: String = "/system/bin/sh"
//  val DEFAULT_ROOT: String = "/system/bin/su"
//  val ALTERNATIVE_ROOT: String = "/system/xbin/su"
//  val DEFAULT_IPTABLES: String = "/data/data/com.biganiseed.reindeer/iptables"
//  val ALTERNATIVE_IPTABLES: String = "/system/bin/iptables"
  val TIME_OUT: Int = -99
//  var initialized: Boolean = false
//  var hasRedirectSupport: Int = -1
//  var isRoot: Int = -1
  var shell: String = null
  var root_shell: String = null
//  var iptables: String = null
//  var data_path: String = null
//  var rootTries = 0

//
//  def isServiceStarted(name: String, context: Context): Boolean = {
//import scala.collection.JavaConversions._
//
//    val activityManager = context
//      .getSystemService(Context.ACTIVITY_SERVICE)
//      .asInstanceOf[ActivityManager]
//    val services = activityManager.getRunningServices(Integer.MAX_VALUE)
//
//    if (services != null) {
//      for (service <- services) {
//        if (service.service.getClassName == name) {
//          return true
//        }
//      }
//    }
//
//    false
//  }
//
////  def resolve(host: String, addrType: Int): Option[String] = {
////    val lookup = new Lookup(host, addrType)
////    val resolver = new SimpleResolver("114.114.114.114")
////    resolver.setTimeout(5)
////    lookup.setResolver(resolver)
////    val result = lookup.run()
////    if (result == null) return None
////    val records = scala.util.Random.shuffle(result.toList)
////    for (r <- records) {
////      addrType match {
////        case Type.A =>
////          return Some(r.asInstanceOf[ARecord].getAddress.getHostAddress)
////        case Type.AAAA =>
////          return Some(r.asInstanceOf[AAAARecord].getAddress.getHostAddress)
////      }
////    }
////    None
////  }
//
//  def resolve(host: String): Option[String] = {
//    try {
//      val addr = InetAddress.getByName(host)
//      Some(addr.getHostAddress)
//    } catch {
//      case e: UnknownHostException => None
//    }
//  }
//
////  def resolve(host: String, enableIPv6: Boolean): Option[String] = {
////    if (enableIPv6 && Utils.isIPv6Support) {
////      resolve(host, Type.AAAA) match {
////        case Some(addr) => {
////          return Some(addr)
////        }
////        case None =>
////      }
////    }
////    resolve(host, Type.A) match {
////      case Some(addr) => {
////        return Some(addr)
////      }
////      case None =>
////    }
////    resolve(host) match {
////      case Some(addr) => {
////        return Some(addr)
////      }
////      case None =>
////    }
////    None
////  }
//
//  /**
//   * Get local IPv4 address
//   */
//  def getIPv4Address: Option[String] = {
//    try {
//      val interfaces = NetworkInterface.getNetworkInterfaces
//      while (interfaces.hasMoreElements) {
//        val intf = interfaces.nextElement()
//        val addrs = intf.getInetAddresses
//        while (addrs.hasMoreElements) {
//          val addr = addrs.nextElement()
//          if (!addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
//            val sAddr = addr.getHostAddress.toUpperCase
//            if (InetAddressUtils.isIPv4Address(sAddr)) {
//              return Some(sAddr)
//            }
//          }
//        }
//      }
//    } catch {
//      case ex: Exception => {
//        Log.e(TAG, "Failed to get interfaces' addresses.", ex)
//      }
//    }
//    None
//  }
//
//  /**
//   * If there exists a valid IPv6 interface
//   */
//  def isIPv6Support: Boolean = {
//    try {
//      val interfaces = NetworkInterface.getNetworkInterfaces
//      while (interfaces.hasMoreElements) {
//        val intf = interfaces.nextElement()
//        val addrs = intf.getInetAddresses
//        while (addrs.hasMoreElements) {
//          val addr = addrs.nextElement()
//          if (!addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
//            val sAddr = addr.getHostAddress.toUpperCase
//            if (InetAddressUtils.isIPv6Address(sAddr)) {
//              if (SSBuildConfig.DEBUG) Log.d(TAG, "IPv6 address detected")
//              return true
//            }
//          }
//        }
//      }
//    } catch {
//      case ex: Exception => {
//        Log.e(TAG, "Failed to get interfaces' addresses.", ex)
//      }
//    }
//    false
//  }

  /**
   * Get the ABI of the device
   * @return The ABI of the device, or ARM_ABI if not found
   */
  def getABI: String = {
    val prop = getSystemProperty(ABI_PROP)
    prop match {
      case abi if abi.toLowerCase.contains(ARM_ABI) => ARM_ABI
      case abi if abi.toLowerCase.contains(X86_ABI) => X86_ABI
      case _ => ARM_ABI
    }
  }

  /**
   * Returns a SystemProperty
   *
   * @param propName The Property to retrieve
   * @return The Property, or NULL if not found
   */
  def getSystemProperty(propName: String): String = {
    val p: Process = Runtime.getRuntime.exec("getprop " + propName)
    val lines = scala.io.Source.fromInputStream(p.getInputStream).getLines()
    if (lines.hasNext) lines.next() else null
  }

//  /**
//   * Check the system's iptables
//   * Default to use the app's iptables
//   */
//  def checkIptables() {
//
//    if (!Utils.getRoot) {
//      iptables = DEFAULT_IPTABLES
//      return
//    }
//
//    iptables = DEFAULT_IPTABLES
//
//    var lines: String = null
//    var compatible: Boolean = false
//    var version: Boolean = false
//
//    val sb = new StringBuilder
//    val command = iptables + " --version\n" + iptables + " -L -t nat -n\n" + "exit\n"
//    val exitcode = runScript(command, sb, 10 * 1000, asroot = true)
//    if (exitcode == TIME_OUT) return
//
//    lines = sb.toString()
//    if (lines.contains("OUTPUT")) {
//      compatible = true
//    }
//    if (lines.contains("v1.4.")) {
//      version = true
//    }
//    if (!compatible || !version) {
//      iptables = ALTERNATIVE_IPTABLES
//      if (!new File(iptables).exists) iptables = "iptables"
//    }
//  }
//
//  def drawableToBitmap(drawable: Drawable): Bitmap = {
//    drawable match {
//      case d: BitmapDrawable =>
//        return d.getBitmap
//      case _ =>
//    }
//
//    val width = if (drawable.getIntrinsicWidth > 0) drawable.getIntrinsicWidth else 1
//    val height = if (drawable.getIntrinsicWidth > 0) drawable.getIntrinsicWidth else 1
//
//    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
//    val canvas = new Canvas(bitmap)
//    drawable.setBounds(0, 0, canvas.getWidth, canvas.getHeight)
//    drawable.draw(canvas)
//
//    bitmap
//  }
//
//  def getAppIcon(c: Context, uid: Int): Drawable = {
//    val pm: PackageManager = c.getPackageManager
//    val icon: Drawable = c.getResources.getDrawable(android.R.drawable.sym_def_app_icon)
//    val packages: Array[String] = pm.getPackagesForUid(uid)
//    if (packages != null) {
//      if (packages.length >= 1) {
//        try {
//          val appInfo: ApplicationInfo = pm.getApplicationInfo(packages(0), 0)
//          return pm.getApplicationIcon(appInfo)
//        } catch {
//          case e: PackageManager.NameNotFoundException => {
//            Log.e(c.getPackageName, "No package found matching with the uid " + uid)
//          }
//        }
//      }
//    } else {
//      Log.e(c.getPackageName, "Package not found for uid " + uid)
//    }
//    icon
//  }
//
//  def getHasRedirectSupport: Boolean = {
//    if (hasRedirectSupport == -1) initHasRedirectSupported()
//    hasRedirectSupport == 1
//  }
//
//  def getIptables: String = {
//    if (iptables == null) checkIptables()
//    iptables
//  }
//
  def getShell: String = {
    if (shell == null) {
      shell = DEFAULT_SHELL
      if (!new File(shell).exists) shell = "sh"
    }
    shell
  }

//  def initHasRedirectSupported() {
//    if (!Utils.getRoot) return
//    hasRedirectSupport = 1
//
//    val sb = new StringBuilder
//    val command = Utils.getIptables + " -t nat -A OUTPUT -p udp --dport 54 -j REDIRECT --to 8154"
//    val exitcode: Int = runScript(command, sb, 10 * 1000, asroot = true)
//    val lines = sb.toString()
//
//    Utils.runRootCommand(command.replace("-A", "-D"))
//    if (exitcode == TIME_OUT) return
//    if (lines.contains("No chain/target/match")) {
//      hasRedirectSupport = 0
//    }
//  }
//
//  def isInitialized: Boolean = {
//    initialized match {
//      case true => true
//      case _ => {
//        initialized = true
//        false
//      }
//    }
//  }
//
//  def getRoot: Boolean = {
//    if (isRoot != -1) return isRoot == 1
//    if (new File(DEFAULT_ROOT).exists) {
//      root_shell = DEFAULT_ROOT
//    } else if (new File(ALTERNATIVE_ROOT).exists) {
//      root_shell = ALTERNATIVE_ROOT
//    } else {
//      root_shell = "su"
//    }
//    val sb = new StringBuilder
//    val command: String = "id\n"
//    val exitcode: Int = runScript(command, sb, 10 * 1000, asroot = true)
//    if (exitcode == TIME_OUT) {
//      return false
//    }
//    val lines = sb.toString()
//    if (lines.contains("uid=0")) {
//      isRoot = 1
//    } else {
//      if (rootTries >= 1) isRoot = 0
//      rootTries += 1
//    }
//    isRoot == 1
//  }
//
  def runCommand(command: String): Boolean = {
    runCommand(command, 10 * 1000)
  }

  def runCommand(command: String, timeout: Int): Boolean = {
    if (SSBuildConfig.DEBUG) Log.d(TAG, command)
    runScript(command, null, timeout, asroot = false)
    true
  }
//
//  def runRootCommand(command: String): Boolean = {
//    runRootCommand(command, 10 * 1000)
//  }
//
//  def runRootCommand(command: String, timeout: Int): Boolean = {
//    if (!Utils.getRoot) {
//      Log.e(TAG, "Cannot get ROOT permission: " + root_shell)
//      return false
//    }
//    if (SSBuildConfig.DEBUG) Log.d(TAG, command)
//    runScript(command, null, timeout, asroot = true)
//    true
//  }

  def runScript(script: String, result: StringBuilder, timeout: Long, asroot: Boolean): Int = {
    val runner: ReindeerUtils.ScriptRunner = new ReindeerUtils.ScriptRunner(script, result, asroot)
    runner.start()
    try {
      if (timeout > 0) {
        runner.join(timeout)
      } else {
        runner.join()
      }
      if (runner.isAlive) {
        runner.destroy()
        runner.join(1000)
        return TIME_OUT
      }
    } catch {
      case ex: InterruptedException => {
        return TIME_OUT
      }
    }
    runner.exitcode
  }

  /**
   * Internal thread used to execute scripts (as root or not).
   * Creates a new script runner.
   *
   * @param scripts script to run
   * @param result result output
   * @param asroot if true, executes the script as root
   */
  class ScriptRunner(val scripts: String, val result: StringBuilder, val asroot: Boolean)
    extends Thread {

    var exitcode: Int = -1
    val pid: Array[Int] = new Array[Int](1)
    var pipe: FileDescriptor = null

    override def destroy() {
      if (pid(0) != -1) {
        Exec.hangupProcessGroup(pid(0))
        pid(0) = -1
      }
      if (pipe != null) {
        Exec.close(pipe)
        pipe = null
      }
    }

    def createSubprocess(processId: Array[Int], cmd: String): FileDescriptor = {
      val args = parse(cmd)
      val arg0 = args(0)
      Exec
        .createSubprocess(if (result != null) 1 else 0, arg0, args, null, scripts + "\nexit\n",
        processId)
    }

    def parse(cmd: String): Array[String] = {
      val PLAIN = 0
      val INQUOTE = 2
      val SKIP = 3

      var state = PLAIN
      val result: ArrayBuffer[String] = new ArrayBuffer[String]
      val builder = new StringBuilder()

      cmd foreach {
        ch => {
          state match {
            case PLAIN => {
              ch match {
                case c if Character.isWhitespace(c) => {
                  result += builder.toString
                  builder.clear()
                }
                case '"' => state = INQUOTE
                case _ => builder += ch
              }
            }
            case INQUOTE => {
              ch match {
                case '\\' => state = SKIP
                case '"' => state = PLAIN
                case _ => builder += ch
              }
            }
            case SKIP => {
              builder += ch
              state = INQUOTE
            }
          }
        }
      }

      if (builder.length > 0) {
        result += builder.toString
      }
      result.toArray
    }

    override def run() {
      pid(0) = -1
      try {
        if (this.asroot) {
          pipe = createSubprocess(pid, root_shell)
        } else {
          pipe = createSubprocess(pid, getShell)
        }
        if (pid(0) != -1) {
          exitcode = Exec.waitFor(pid(0))
        }
        if (result == null || pipe == null) return
        val stdout: InputStream = new FileInputStream(pipe)
        val buf = new Array[Byte](8192)
        var read: Int = 0
        while (stdout.available > 0) {
          read = stdout.read(buf)
          result.append(new String(buf, 0, read))
        }
      } catch {
        case ex: Exception => {
          Log.e(TAG, "Cannot execute command", ex)
          if (result != null) result.append("\n").append(ex)
        }
      } finally {
        if (pipe != null) {
          Exec.close(pipe)
        }
        if (pid(0) != -1) {
          Exec.hangupProcessGroup(pid(0))
        }
      }
    }
  }
  
  def copyAssets(context: Context, execPath: String, path: String) {
    val assetManager: AssetManager = context.getAssets
    var files: Array[String] = null
    try {
      files = assetManager.list(path)
    } catch {
      case e: IOException => {
        Log.e(TAG, e.getMessage)
      }
    }
   if (files != null) {
       for (file <- files) {
        var in: InputStream = null
        var out: OutputStream = null
        try {
          if (path.length > 0) {
            in = assetManager.open(path + "/" + file)
          } else {
            in = assetManager.open(file)
          }
          out = new FileOutputStream(execPath + file)
          copyFile(in, out)
          in.close()
          in = null
          out.flush()
          out.close()
          out = null
        } catch {
          case ex: Exception => {
            Log.e(TAG, ex.getMessage)
          }
        }
      }
    }
  }

  private def copyFile(in: InputStream, out: OutputStream) {
    val buffer: Array[Byte] = new Array[Byte](1024)
    var read: Int = 0
    while ( {
      read = in.read(buffer)
      read
    } != -1) {
      out.write(buffer, 0, read)
    }
  }

  def crash_recovery(execPath: String) {
    val sb = new StringBuilder

    sb.append("kill -9 `cat "+ execPath + "pdnsd.pid`").append("\n")
    sb.append("kill -9 `cat "+ execPath + "shadowsocks.pid`").append("\n")
    sb.append("kill -9 `cat "+ execPath + "tun2socks.pid`").append("\n")
    sb.append("killall -9 pdnsd").append("\n")
    sb.append("killall -9 shadowsocks").append("\n")
    sb.append("killall -9 tun2socks").append("\n")
    sb.append("rm "+ execPath + "pdnsd.conf").append("\n")
    ReindeerUtils.runCommand(sb.toString())

//    sb.clear()
//    sb.append("kill -9 `cat /data/data/com.biganiseed.reindeer/redsocks.pid`").append("\n")
//    sb.append("killall -9 redsocks").append("\n")
//    sb.append("rm /data/data/com.biganiseed.reindeer/redsocks.conf").append("\n")
//    sb.append(Utils.getIptables).append(" -t nat -F OUTPUT").append("\n")
//    Utils.runRootCommand(sb.toString())
  }

//  def reset(context: Context, execPath: String) {
//    crash_recovery(execPath)
//    copyAssets(context, execPath, ReindeerUtils.getABI)
//    chmodAssets(execPath)
//  }

  def chmodAssets(execPath: String){
    ReindeerUtils.runCommand("chmod 755 " + execPath + "iptables\n"
      + "chmod 755 " + execPath + "redsocks\n"
      + "chmod 755 " + execPath + "pdnsd\n"
      + "chmod 755 " + execPath + "shadowsocks\n"
      + "chmod 755 " + execPath + "tun2socks\n")
  }
  
  def getExecPath(app: android.app.Application): String = {
    app.getDir("exec", 0).getAbsolutePath() + "/";
  }
}
