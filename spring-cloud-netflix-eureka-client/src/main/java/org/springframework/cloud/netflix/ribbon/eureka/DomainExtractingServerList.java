/*
 * Copyright 2013-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.netflix.ribbon.eureka;

import java.util.ArrayList;
import java.util.List;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;
import com.netflix.niws.loadbalancer.DiscoveryEnabledServer;

/**
 * @author Dave Syer
 */
public class DomainExtractingServerList implements ServerList<DiscoveryEnabledServer> {

	private ServerList<DiscoveryEnabledServer> list;

	private IClientConfig clientConfig;

	private boolean approximateZoneFromHostname;

	public DomainExtractingServerList(ServerList<DiscoveryEnabledServer> list,
			IClientConfig clientConfig, boolean approximateZoneFromHostname) {
		this.list = list;
		this.clientConfig = clientConfig;
		this.approximateZoneFromHostname = approximateZoneFromHostname;
	}

	@Override
	public List<DiscoveryEnabledServer> getInitialListOfServers() {
		List<DiscoveryEnabledServer> servers = setZones(this.list
				.getInitialListOfServers());
		return servers;
	}
/**
2018/8/2-8:24白初心iceu

 class DiscoveryEnabledNIWSServerList extends AbstractServerList<DiscoveryEnabledServer>
 ServerList<DiscoveryEnabledServer> list


 public List<DiscoveryEnabledServer> getUpdatedListOfServers() {
 return this.obtainServersViaDiscovery();


 private List<DiscoveryEnabledServer> obtainServersViaDiscovery() {
 List<DiscoveryEnabledServer> serverList = new ArrayList();
 if (this.eurekaClientProvider != null && this.eurekaClientProvider.get() != null) {
 EurekaClient eurekaClient = (EurekaClient)this.eurekaClientProvider.get();

  这个 vipAdress 就是服务名称 serviceA
 if (this.vipAddresses != null) {
 String[] var3 = this.vipAddresses.split(",");
 int var4 = var3.length;

 for(int var5 = 0; var5 < var4; ++var5) {
 String vipAddress = var3[var5];

 根据服务名称切获取注册表
 List<InstanceInfo> listOfInstanceInfo = eurekaClient.getInstancesByVipAddress(vipAddress, this.isSecure, this.targetRegion);

 从DiscoveryClient中的方法
 -------------------------------
 public List<InstanceInfo> getInstancesByVipAddress(String vipAddress, boolean secure, @Nullable String region) {
 if (vipAddress == null) {
 throw new IllegalArgumentException("Supplied VIP Address cannot be null");
 } else {
 Applications applications;
 if (this.instanceRegionChecker.isLocalRegion(region)) {
 applications = (Applications)this.localRegionApps.get();
 } else {
 applications = (Applications)this.remoteRegionVsApps.get(region);
 if (null == applications) {
 logger.debug("No applications are defined for region {}, so returning an empty instance list for vip address {}.", region, vipAddress);
 return Collections.emptyList();
 }
 }
-------------------------------------


 return !secure ? applications.getInstancesByVirtualHostName(vipAddress) : applications.getInstancesBySecureVirtualHostName(vipAddress);
 }
 }

 Iterator var8 = listOfInstanceInfo.iterator();

 while(var8.hasNext()) {
 InstanceInfo ii = (InstanceInfo)var8.next();
 if (ii.getStatus().equals(InstanceStatus.UP)) {
 if (this.shouldUseOverridePort) {
 if (logger.isDebugEnabled()) {
 logger.debug("Overriding port on client name: " + this.clientName + " to " + this.overridePort);
 }

 InstanceInfo copy = new InstanceInfo(ii);
 if (this.isSecure) {
 ii = (new Builder(copy)).setSecurePort(this.overridePort).build();
 } else {
 ii = (new Builder(copy)).setPort(this.overridePort).build();
 }
 }

 DiscoveryEnabledServer des = new DiscoveryEnabledServer(ii, this.isSecure, this.shouldUseIpAddr);
 des.setZone(DiscoveryClient.getZone(ii));
 serverList.add(des);
 }
 }

 if (serverList.size() > 0 && this.prioritizeVipAddressBasedServers) {
 break;
 }
 }
 }

 return serverList;
 } else {
 logger.warn("EurekaClient has not been initialized yet, returning an empty list");
 return new ArrayList();
 }
 }
 其实调用的是这个方法
 在这个obtainServersviaDicovery（）方法里面 有一堆eureka相关的代码
 从eureka client中获取到注册表 从注册表中获取当前这个服务的ServiceAble的对应的
 server list
 }
*/
	@Override
	public List<DiscoveryEnabledServer> getUpdatedListOfServers() {
		List<DiscoveryEnabledServer> servers = setZones(this.list
				.getUpdatedListOfServers());
		return servers;
	}

	private List<DiscoveryEnabledServer> setZones(List<DiscoveryEnabledServer> servers) {
		List<DiscoveryEnabledServer> result = new ArrayList<>();
		boolean isSecure = this.clientConfig.getPropertyAsBoolean(
				CommonClientConfigKey.IsSecure, Boolean.TRUE);
		boolean shouldUseIpAddr = this.clientConfig.getPropertyAsBoolean(
				CommonClientConfigKey.UseIPAddrForServer, Boolean.FALSE);
		for (DiscoveryEnabledServer server : servers) {
			result.add(new DomainExtractingServer(server, isSecure, shouldUseIpAddr,
					this.approximateZoneFromHostname));
		}
		return result;
	}

}

class DomainExtractingServer extends DiscoveryEnabledServer {

	private String id;

	@Override
	public String getId() {
		return id;
	}

	@Override
	public void setId(String id) {
		this.id = id;
	}

	public DomainExtractingServer(DiscoveryEnabledServer server, boolean useSecurePort,
			boolean useIpAddr, boolean approximateZoneFromHostname) {
		// host and port are set in super()
		super(server.getInstanceInfo(), useSecurePort, useIpAddr);
		if (server.getInstanceInfo().getMetadata().containsKey("zone")) {
			setZone(server.getInstanceInfo().getMetadata().get("zone"));
		}
		else if (approximateZoneFromHostname) {
			setZone(ZoneUtils.extractApproximateZone(server.getHost()));
		}
		else {
			setZone(server.getZone());
		}
		setId(extractId(server));
		setAlive(server.isAlive());
		setReadyToServe(server.isReadyToServe());
	}

	private String extractId(Server server) {
		if (server instanceof DiscoveryEnabledServer) {
			DiscoveryEnabledServer enabled = (DiscoveryEnabledServer) server;
			InstanceInfo instance = enabled.getInstanceInfo();
			if (instance.getMetadata().containsKey("instanceId")) {
				return instance.getHostName()+":"+instance.getMetadata().get("instanceId");
			}
		}
		return super.getId();
	}
}
