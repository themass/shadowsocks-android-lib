/*
 * Shadowsocks - A shadowsocks client for Android
 * Copyright (C) 2013 <max.c.lv@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 *                            ___====-_  _-====___
 *                      _--^^^#####//      \\#####^^^--_
 *                   _-^##########// (    ) \\##########^-_
 *                  -############//  |\^^/|  \\############-
 *                _/############//   (@::@)   \\############\_
 *               /#############((     \\//     ))#############\
 *              -###############\\    (oo)    //###############-
 *             -#################\\  / VV \  //#################-
 *            -###################\\/      \//###################-
 *           _#/|##########/\######(   /\   )######/\##########|\#_
 *           |/ |#/\#/\#/\/  \#/\##\  |  |  /##/\#/  \/\#/\#/\#| \|
 *           `  |/  V  V  `   V  \#\| |  | |/#/  V   '  V  V  \|  '
 *              `   `  `      `   / | |  | | \   '      '  '   '
 *                               (  | |  | |  )
 *                              __\ | |  | | /__
 *                             (vvv(VVV)(VVV)vvv)
 *
 *                              HERE BE DRAGONS
 *
 */

package com.github.shadowsocks

import android.app._
import android.content._
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os._
//import android.support.v4.app.NotificationCompat
import android.util.Log
//import com.google.analytics.tracking.android.{Fields, MapBuilder, EasyTracker}
import java.io._
import android.net.VpnService
import org.apache.http.conn.util.InetAddressUtils
import android.os.Message
import scala.Some
import scala.concurrent.ops._
import org.apache.commons.net.util.SubnetUtils
import java.net.InetAddress
import com.github.shadowsocks.utils._
import scala.Some
//import com.github.shadowsocks.ProxiedApp


object ShadowVpnService {
//  def isServiceStarted(context: Context): Boolean = {
//    Utils.isServiceStarted("com.github.shadowsocks.ShadowVpnService", context)
//  }
  var base: String = null 
  def setBase(value: String) {
    base = value;
  }

  def getBase(): String = {
    assert(base != null, "Must set base with the package path before start the service")
    base
  }

}

class ShadowVpnService extends VpnService {

  val TAG = "ShadowVpnService"
//  val BASE = "/data/data/com.biganiseed.reindeer/"

  val MSG_CONNECT_FINISH = 1
  val MSG_CONNECT_SUCCESS = 2
  val MSG_CONNECT_FAIL = 3
  val MSG_STOP_SELF = 5
  val MSG_VPN_ERROR = 6

//  val VPN_MTU = 1300 //cause many phone ROM can not access internet
  val VPN_MTU = 1500

  val PRIVATE_VLAN = "26.26.26.%s"

  var conn: ParcelFileDescriptor = null
  var notificationManager: NotificationManager = null
  var receiver: BroadcastReceiver = null
//  var apps: Array[ProxiedApp] = null
  var config: Config = null

  private var state = State.INIT
  private var message: String = null

  def changeState(s: Int) {
    changeState(s, null)
  }

  def changeState(s: Int, m: String) {
    if (state != s) {
      state = s
      if (m != null) message = m
      val intent = new Intent(Action.UPDATE_STATE)
      intent.putExtra(Extra.STATE, state)
      intent.putExtra(Extra.MESSAGE, message)
      sendBroadcast(intent)
    }
  }

  val handler: Handler = new Handler {
    override def handleMessage(msg: Message) {
      msg.what match {
        case MSG_CONNECT_SUCCESS =>
          changeState(State.CONNECTED)
        case MSG_CONNECT_FAIL =>
          changeState(State.STOPPED)
        case MSG_VPN_ERROR =>
          if (msg.obj != null) changeState(State.STOPPED, msg.obj.asInstanceOf[String])
        case MSG_STOP_SELF =>
          destroy()
          stopSelf()
        case _ =>
      }
      super.handleMessage(msg)
    }
  }

  def getPid(name: String): Int = {
    try {
      val reader: BufferedReader = new BufferedReader(new FileReader(ShadowVpnService.getBase + name + ".pid"))
      val line = reader.readLine
      return Integer.valueOf(line)
    } catch {
      case e: FileNotFoundException => {
        Log.e(TAG, "Cannot open pid file: " + name)
      }
      case e: IOException => {
        Log.e(TAG, "Cannot read pid file: " + name)
      }
      case e: NumberFormatException => {
        Log.e(TAG, "Invalid pid", e)
      }
    }
    -1
  }

  def startShadowsocksDaemon() {
    val cmd: String = (ShadowVpnService.getBase +
      "shadowsocks -b 127.0.0.1 -s \"%s\" -p \"%d\" -l \"%d\" -k \"%s\" -m \"%s\" -f " +
      ShadowVpnService.getBase + "shadowsocks.pid")
      .format(config.proxy, config.remotePort, config.localPort, config.sitekey, config.encMethod)
    if (SSBuildConfig.DEBUG) Log.d(TAG, cmd)
    System.exec(cmd)
  }

  def startDnsDaemon() {
    val cmd: String = ShadowVpnService.getBase + "pdnsd -c " + ShadowVpnService.getBase + "pdnsd.conf"
    val conf: String = Config.PDNSD.format(ShadowVpnService.getBase, "0.0.0.0", ShadowVpnService.getBase + "pdnsd.pid")
    Config.printToFile(new File(ShadowVpnService.getBase + "pdnsd.conf"))(p => {
      p.println(conf)
    })
    Utils.runCommand(cmd)
  }

  def getVersionName: String = {
    var version: String = null
    try {
      val pi: PackageInfo = getPackageManager.getPackageInfo(getPackageName, 0)
      version = pi.versionName
    } catch {
      case e: PackageManager.NameNotFoundException => {
        version = "Package name not found"
      }
    }
    version
  }

  def handleCommand(intent: Intent) {
    if (intent == null) {
      stopSelf()
      return
    }

    if (VpnService.prepare(this) != null) {
//      val i = new Intent(this, classOf[ShadowVpnActivity])
//      i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//      startActivity(i)
      stopSelf()
      return
    }

	changeState(State.CONNECTING)

    config = Extra.get(intent)

    spawn {
//      if (config.proxy == "198.199.101.152") {
//        val container = getApplication.asInstanceOf[ShadowsocksApplication].tagContainer
//        try {
//          config = Config.getPublicConfig(getBaseContext, container, config)
//        } catch {
//          case ex: Exception => {
//            notifyAlert(getString(R.string.forward_fail), getString(R.string.service_failed))
//            stopSelf()
//            handler.sendEmptyMessageDelayed(MSG_CONNECT_FAIL, 500)
//            return
//          }
//        }
//      }

      killProcesses()

      // Resolve server address
      var resolved: Boolean = false
      if (!InetAddressUtils.isIPv4Address(config.proxy) &&
        !InetAddressUtils.isIPv6Address(config.proxy)) {
        Utils.resolve(config.proxy, enableIPv6 = true) match {
          case Some(addr) =>
            config.proxy = addr
            resolved = true
          case None => resolved = false
        }
      } else {
        resolved = true
      }

      if (resolved && handleConnection) {
        handler.sendEmptyMessageDelayed(MSG_CONNECT_SUCCESS, 300)
      } else {
//        notifyAlert(getString(R.string.forward_fail), getString(R.string.service_failed))
        handler.sendEmptyMessageDelayed(MSG_CONNECT_FAIL, 300)
        handler.sendEmptyMessageDelayed(MSG_STOP_SELF, 500)
      }
      handler.sendEmptyMessageDelayed(MSG_CONNECT_FINISH, 300)
    }
  }

  def waitForProcess(name: String): Boolean = {
    val pid: Int = getPid(name)
    if (pid == -1) return false
    Exec.hangupProcessGroup(-pid)
    val t: Thread = new Thread {
      override def run() {
        Exec.waitFor(-pid)
      }
    }
    t.start()
    try {
      t.join(300)
    } catch {
      case ignored: InterruptedException => {
      }
    }
    !t.isAlive
  }

  def startVpn() {

    val proxy_address = config.proxy

    val builder = new Builder()
    builder
      .setSession(config.profileName)
      .setMtu(VPN_MTU)
      .addAddress(PRIVATE_VLAN.format("1"), 24)
      .addDnsServer("8.8.8.8")
      .addDnsServer("8.8.4.4")

    if (InetAddressUtils.isIPv6Address(config.proxy)) {
      builder.addRoute("0.0.0.0", 0)
    } else if (config.isGFWList) {
      val gfwList = {
        if (Build.VERSION.SDK_INT == 19) {
          getResources.getStringArray(R.array.simple_list)
        } else {
          getResources.getStringArray(R.array.gfw_list)
        }
      }
      gfwList.foreach(cidr => {
        val net = new SubnetUtils(cidr).getInfo
        if (!net.isInRange(proxy_address)) {
          val addr = cidr.split('/')
          builder.addRoute(addr(0), addr(1).toInt)
        }
      })
    } else {
      for (i <- 1 to 223) {
        if (i != 26 && i != 127) {
          val addr = i.toString + ".0.0.0"
          val cidr = addr + "/8"
          val net = new SubnetUtils(cidr).getInfo

          if (!net.isInRange(proxy_address)) {
            if (!InetAddress.getByName(addr).isSiteLocalAddress) {
              builder.addRoute(addr, 8)
            }
          } else {
            for (j <- 0 to 255) {
              val subAddr = i.toString + "." + j.toString + ".0.0"
              val subCidr = subAddr + "/16"
              val subNet = new SubnetUtils(subCidr).getInfo
              if (!subNet.isInRange(proxy_address)) {
                if (!InetAddress.getByName(subAddr).isSiteLocalAddress) {
                  builder.addRoute(subAddr, 16)
                }
              }
            }
          }
        }
      }
    }

    builder.addRoute("8.8.0.0", 16)

    try {
      conn = builder.establish()
    } catch {
      case ex: IllegalStateException => {
        val msg = new Message()
        msg.what = MSG_VPN_ERROR
        msg.obj = ex.getMessage
        handler.sendMessage(msg)
        conn = null
      }
      case ex: Exception => conn = null
    }

    if (conn == null) {
      stopSelf()
      return
    }

//    val fd = conn.getFd
    val fd = if(isVip) conn.getFd else (new VpnRelayPipe(this, conn)).connect().getFd()
    
    val cmd = (ShadowVpnService.getBase +
      "tun2socks --netif-ipaddr %s "
      + "--dnsgw  %s:8153 "
      + "--netif-netmask 255.255.255.0 "
      + "--socks-server-addr 127.0.0.1:%d "
      + "--tunfd %d "
      + "--tunmtu %d "
      + "--loglevel 3 "
      + "--pid %stun2socks.pid")
      .format(PRIVATE_VLAN.format("2"), PRIVATE_VLAN.format("1"), config.localPort, fd, VPN_MTU,
      ShadowVpnService.getBase)
    if (SSBuildConfig.DEBUG) Log.d(TAG, cmd)
    System.exec(cmd)
  }

  /** Called when the activity is first created. */
  def handleConnection: Boolean = {
    startVpn()
    startShadowsocksDaemon()
    startDnsDaemon()
    true
  }

  def initSoundVibrateLights(notification: Notification) {
    notification.sound = null
    notification.defaults |= Notification.DEFAULT_LIGHTS
  }

//  def notifyAlert(title: String, info: String) {
//    val openIntent: Intent = new Intent(this, classOf[Shadowsocks])
//    openIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
//    val contentIntent: PendingIntent = PendingIntent.getActivity(this, 0, openIntent, 0)
//    val builder: NotificationCompat.Builder = new NotificationCompat.Builder(this)
//    builder
//      .setSmallIcon(R.drawable.ic_stat_shadowsocks)
//      .setWhen(0)
//      .setTicker(title)
//      .setContentTitle(getString(R.string.app_name))
//      .setContentText(info)
//      .setContentIntent(contentIntent)
//      .setAutoCancel(true)
//    notificationManager.notify(1, builder.build)
//  }

  override def onBind(intent: Intent): IBinder = {
    val action = intent.getAction
    if (VpnService.SERVICE_INTERFACE == action) {
      return super.onBind(intent)
    }
    null
  }

  override def onCreate() {
    super.onCreate()

//    Config.refresh(this)
//
//    EasyTracker
//      .getInstance(this)
//      .send(MapBuilder
//      .createEvent(TAG, "start", getVersionName, 0L)
//      .set(Fields.SESSION_CONTROL, "start")
//      .build())

    notificationManager = getSystemService(Context.NOTIFICATION_SERVICE)
      .asInstanceOf[NotificationManager]

    // register close receiver
    val filter = new IntentFilter()
    filter.addAction(Intent.ACTION_SHUTDOWN)
    filter.addAction(Action.CLOSE)
    receiver = new BroadcastReceiver {
      def onReceive(p1: Context, p2: Intent) {
        destroy()
        stopSelf()
      }
    }
    registerReceiver(receiver, filter)
  }

  def destroy() {
    killProcesses()

    changeState(State.STOPPED)

//    EasyTracker
//      .getInstance(this)
//      .send(MapBuilder
//      .createEvent(TAG, "stop", getVersionName, 0L)
//      .set(Fields.SESSION_CONTROL, "stop")
//      .build())

    if (receiver != null) {
      unregisterReceiver(receiver)
      receiver = null
    }
    if (conn != null) {
      conn.close()
      conn = null
    }
    notificationManager.cancel(1)
  }

  /** Called when the activity is closed. */
  override def onDestroy() {
    destroy()
    super.onDestroy()
  }

  def killProcesses() {
    val sb = new StringBuilder
    if (!waitForProcess("shadowsocks")) {
      sb ++= "kill -9 `cat " + ShadowVpnService.getBase + "shadowsocks.pid`" ++= "\n"
      sb ++= "killall -9 shadowsocks" ++= "\n"
    }
    if (!waitForProcess("tun2socks")) {
      sb ++= "kill -9 `cat " + ShadowVpnService.getBase + "tun2socks.pid`" ++= "\n"
      sb ++= "killall -9 tun2socks" ++= "\n"
    }
    if (!waitForProcess("pdnsd")) {
      sb ++= "kill -9 `cat " + ShadowVpnService.getBase + "pdnsd.pid`" ++= "\n"
      sb ++= "killall -9 pdnsd" ++= "\n"
    }
    Utils.runCommand(sb.toString())
  }

  override def onStart(intent: Intent, startId: Int) {
    handleCommand(intent)
  }

  var isVip: Boolean = false
  override def onStartCommand(intent: Intent, flags: Int, startId: Int): Int = {
    if(intent != null) isVip = intent.getBooleanExtra("isVip", false)
    handleCommand(intent)
    Service.START_STICKY
  }

  override def onRevoke() {
    stopSelf()
  }
}
