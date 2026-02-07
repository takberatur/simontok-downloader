package com.agcforge.videodownloader.data.api

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

class FallbackDns(
	private val primary: Dns = Dns.SYSTEM,
	private val fallbackIpByHost: Map<String, String>
) : Dns {
	override fun lookup(hostname: String): List<InetAddress> {
		try {
			return primary.lookup(hostname)
		} catch (e: UnknownHostException) {
			val ip = fallbackIpByHost[hostname] ?: throw e
			return listOf(InetAddress.getByName(ip))
		}
	}
}

