package com.github.shadowsocks;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Date;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

public class VpnRelayPipe {
	public static final String PREFS = "Reindeer";
	ParcelFileDescriptor mSrcFD;
	ParcelFileDescriptor mDstFD;
	DatagramSocket mRelaySocket;
	DatagramSocket mDstSocket;
	boolean mStop = false; // set it to stop relay gracefully. but for simplicity, not used for the moment
	
	public VpnRelayPipe(Context context, ParcelFileDescriptor srcFD) throws SocketException, UnknownHostException{
		mSrcFD = srcFD;
//		Log.d(ReindeerUtils.TAG(), "relay creating sockets");
		addLog(context, "relay creating sockets");
		mRelaySocket = new DatagramSocket(0, InetAddress.getLocalHost()); //(9040, InetAddress.getLocalHost());
//		mRelaySocket.bind(null);
		mDstSocket = new DatagramSocket(0, InetAddress.getLocalHost()); //(9041, InetAddress.getLocalHost());
//		mDstSocket.bind(null);
//Log.d(ReindeerUtils.TAG(), "relay mRelaySocket port: " + mRelaySocket.getLocalPort());
//Log.d(ReindeerUtils.TAG(), "relay mRelaySocket address: " + mRelaySocket.getLocalAddress());
//Log.d(ReindeerUtils.TAG(), "relay mDstSocket port: " + mDstSocket.getLocalPort());
//Log.d(ReindeerUtils.TAG(), "relay mDstSocket address: " + mDstSocket.getLocalAddress());
		addLog(context, "relay mRelaySocket port: " + mRelaySocket.getLocalPort());
		addLog(context, "relay mDstSocket port: " + mDstSocket.getLocalPort());
		
		mRelaySocket.connect(new InetSocketAddress(mDstSocket.getLocalAddress(), mDstSocket.getLocalPort()));
		mDstSocket.connect(new InetSocketAddress(mRelaySocket.getLocalAddress(), mRelaySocket.getLocalPort()));
		mDstFD = ParcelFileDescriptor.fromDatagramSocket(mDstSocket);
//		Log.d(ReindeerUtils.TAG(), "relay created sockets");
		addLog(context, "relay created sockets");
	}
	
	public void setStop(boolean value){
		mStop = value;
	}
	
//	public ParcelFileDescriptor getDstFD(){
//		return mDstFD;
//	}
//
	public ParcelFileDescriptor connect(){
//		mDstFD = mSrcFD;
		doRelay();
		return mDstFD;
	}

	void doRelay(){
		// dst to src
		new Thread(new Runnable(){
			@Override
			public void run() {
//				FileInputStream inSrc = new FileInputStream(mSrcFD.getFileDescriptor());
				FileOutputStream outSrc = new FileOutputStream(mSrcFD.getFileDescriptor());
				// Allocate the buffer for a single packet.
				byte[] buffer = new byte[65508];
				DatagramPacket packet2Src = new DatagramPacket(buffer, buffer.length);
				
				// We use a timer to determine the status of the tunnel. It
	            // works on both sides. A positive value means sending, and
	            // any other means receiving. We start with receiving.
				try {
		            int timer = 0;
		            // We keep forwarding packets till something goes wrong.
		            while(!mStop){
		                boolean idle = true;
		                // Read the incoming packet from the destination.
//		                int length = inDst.read(buffer);
		                mRelaySocket.receive(packet2Src);
		                byte[] buffer2Src = packet2Src.getData();
		                int length = packet2Src.getLength();
		                if (length > 0) {
//							packet2Src.setData(buffer);
//							packet2Src.setLength(length);
//							mDstSocket.send(packet2Src);
//							outSrc.write(buffer2Src);
							outSrc.write(buffer2Src, 0, length);
//							outSrc.flush();
//							Log.d(ReindeerUtils.TAG(), "relay to source: " + length);
		                    // There might be more incoming packets.
		                    idle = false;
		                    // If we were sending, switch to receiving.
		                    if (timer > 0) {
		                        timer = 0;
		                    }
		                }

		                // If we are idle or waiting for the network, sleep for a
		                // fraction of time to avoid busy looping.
		                if (idle) {
		                    Thread.sleep(100);
		                    // Increase the timer. This is inaccurate but good enough,
		                    // since everything is operated in non-blocking mode.
		                    timer += (timer > 0) ? 100 : -100;
		                    // We are receiving for a long time but not sending.
		                    if (timer < -15000) {
		                        // Switch to sending.
		                        timer = 1;
		                    }
		                    // We are sending for a long time but not receiving.
		                    if (timer > 20000) {
//		                        throw new IllegalStateException("Timed out");
		                    }
		                    Log.d(ReindeerUtils.TAG(), "relay idle:" + timer);
		                }
		            }
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}finally{
					try {
						outSrc.close();
						mRelaySocket.close();
						mDstSocket.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				try {
					outSrc.close();
					mRelaySocket.close();
					mDstSocket.close();
                    Log.d(ReindeerUtils.TAG(), "relay stopped");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}).start();

		// src to dst
		new Thread(new Runnable(){
			@Override
			public void run() {
				FileInputStream inSrc = new FileInputStream(mSrcFD.getFileDescriptor());
//				FileOutputStream outSrc = new FileOutputStream(mSrcFD.getFileDescriptor());
				// Allocate the buffer for a single packet.
				byte[] buffer = new byte[65508];
				DatagramPacket packet2Dst = new DatagramPacket(buffer, buffer.length, mDstSocket.getLocalAddress(), mDstSocket.getLocalPort());
				
				// We use a timer to determine the status of the tunnel. It
	            // works on both sides. A positive value means sending, and
	            // any other means receiving. We start with receiving.
				try {
		            int timer = 0;
		            // We keep forwarding packets till something goes wrong.
		            while(!mStop){
						// Assume that we did not make any progress in this iteration.
		                boolean idle = true;
		                int length = inSrc.read(buffer);
						if (length > 0) {
							packet2Dst.setData(buffer);
							packet2Dst.setLength(length);
							mRelaySocket.send(packet2Dst);
//							Log.d(ReindeerUtils.TAG(), "relay to destination: " + length);
		                    // There might be more outgoing packets.
		                    idle = false;
		                    // If we were receiving, switch to sending.
		                    if (timer < 1) {
		                        timer = 1;
		                    }
		 				}

		                // If we are idle or waiting for the network, sleep for a
		                // fraction of time to avoid busy looping.
		                if (idle) {
		                    Thread.sleep(100);
		                    // Increase the timer. This is inaccurate but good enough,
		                    // since everything is operated in non-blocking mode.
		                    timer += (timer > 0) ? 100 : -100;
		                    // We are receiving for a long time but not sending.
		                    if (timer < -15000) {
		                        // Switch to sending.
		                        timer = 1;
		                    }
		                    // We are sending for a long time but not receiving.
		                    if (timer > 20000) {
//		                        throw new IllegalStateException("Timed out");
		                    }
//		                    Log.d(ReindeerUtils.TAG(), "relay idle:" + timer);
		                }
		            }
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}finally{
					try {
						inSrc.close();
						mRelaySocket.close();
						mDstSocket.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				try {
					inSrc.close();
					mRelaySocket.close();
					mDstSocket.close();
                    Log.d(ReindeerUtils.TAG(), "relay stopped");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}).start();
	
	
	}

	public static void addLog(Context context, String log){
		String time = new Date().toGMTString();
		String row = "\n" + time+" " + log;
		setPrefString(context, "log", getLog(context) + row);
	}

	public static String getLog(Context context){
		return getPrefString(context, "log", "");
	}

	public static String getPrefString(Context context, String key, String defValue){
		SharedPreferences pref = context.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
		return pref.getString(key, defValue);
	}

	public static void setPrefString(Context context, String key, String value){
		SharedPreferences pref = context.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
		SharedPreferences.Editor editor = pref.edit();
		editor.putString(key, value);
		editor.commit();
	}
	
	
}
