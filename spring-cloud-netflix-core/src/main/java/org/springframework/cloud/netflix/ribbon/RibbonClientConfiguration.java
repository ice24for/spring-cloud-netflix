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

package org.springframework.cloud.netflix.ribbon;

import java.net.URI;

import javax.annotation.PostConstruct;

import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.CookiePolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.commons.httpclient.HttpClientConfiguration;
import org.springframework.cloud.netflix.ribbon.apache.HttpClientRibbonConfiguration;
import org.springframework.cloud.netflix.ribbon.okhttp.OkHttpRibbonConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.netflix.client.DefaultLoadBalancerRetryHandler;
import com.netflix.client.RetryHandler;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.ConfigurationBasedServerList;
import com.netflix.loadbalancer.DummyPing;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.IPing;
import com.netflix.loadbalancer.IRule;
import com.netflix.loadbalancer.PollingServerListUpdater;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;
import com.netflix.loadbalancer.ServerListFilter;
import com.netflix.loadbalancer.ServerListUpdater;
import com.netflix.loadbalancer.ZoneAvoidanceRule;
import com.netflix.loadbalancer.ZoneAwareLoadBalancer;
import com.netflix.niws.client.http.RestClient;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.client.apache4.ApacheHttpClient4;

import static com.netflix.client.config.CommonClientConfigKey.DeploymentContextBasedVipAddresses;
import static org.springframework.cloud.netflix.ribbon.RibbonUtils.setRibbonProperty;
import static org.springframework.cloud.netflix.ribbon.RibbonUtils.updateToHttpsIfNeeded;

/**
 * @author Dave Syer
 */
@SuppressWarnings("deprecation")
@Configuration
@EnableConfigurationProperties
//Order is important here, last should be the default, first should be optional
// see https://github.com/spring-cloud/spring-cloud-netflix/issues/2086#issuecomment-316281653
@Import({HttpClientConfiguration.class, OkHttpRibbonConfiguration.class, RestClientRibbonConfiguration.class, HttpClientRibbonConfiguration.class})
public class RibbonClientConfiguration {

	public static final int DEFAULT_CONNECT_TIMEOUT = 1000;
	public static final int DEFAULT_READ_TIMEOUT = 1000;

	@Value("${ribbon.client.name}")
	private String name = "client";

	// TODO: maybe re-instate autowired load balancers: identified by name they could be
	// associated with ribbon clients

	@Autowired
	private PropertiesFactory propertiesFactory;

	@Bean
	@ConditionalOnMissingBean
	public IClientConfig ribbonClientConfig() {
		DefaultClientConfigImpl config = new DefaultClientConfigImpl();
		config.loadProperties(this.name);
		config.set(CommonClientConfigKey.ConnectTimeout, DEFAULT_CONNECT_TIMEOUT);
		config.set(CommonClientConfigKey.ReadTimeout, DEFAULT_READ_TIMEOUT);
		return config;
	}

	@Bean
	@ConditionalOnMissingBean
	public IRule ribbonRule(IClientConfig config) {
		if (this.propertiesFactory.isSet(IRule.class, name)) {
			return this.propertiesFactory.get(IRule.class, config, name);
		}
		ZoneAvoidanceRule rule = new ZoneAvoidanceRule();
		rule.initWithNiwsConfig(config);
		return rule;
	}

	@Bean
	@ConditionalOnMissingBean
	public IPing ribbonPing(IClientConfig config) {
		if (this.propertiesFactory.isSet(IPing.class, name)) {
			return this.propertiesFactory.get(IPing.class, config, name);
		}
		return new DummyPing();
	}


	/**
	2018/8/2-7:44白初心iceu

	 ConfigurationBasedServerList =>Serverlist
	*/
	@Bean
	@ConditionalOnMissingBean
	@SuppressWarnings("unchecked")
	public ServerList<Server> ribbonServerList(IClientConfig config) {
		if (this.propertiesFactory.isSet(ServerList.class, name)) {
			return this.propertiesFactory.get(ServerList.class, config, name);
		}

		/**
		2018/8/2-7:46白初心iceu
		 @Override
		 public List<Server> getUpdatedListOfServers() {
		 String listOfServers = clientConfig.get(CommonClientConfigKey.ListOfServers);
		 return derive(listOfServers);
		 }
		 L看代码 不对 不是我们要找的getUpdatedlistofServers
		 他是从配置文件中 或者是代码的配置中 读取了一个
		 ListOfServers 的配置项 如果我们手动配置了一个个serverList
		 才是走的这个东西 来初始化这个serverList

		 猜测 serverList肯定是从eureka中读取的
		 应该在Eureka的工程或者相关的代码中去读取

		 RibbonEurekaAutoConfiguration eureka和ribbon进行整合的

		 */
		ConfigurationBasedServerList serverList = new ConfigurationBasedServerList();
		serverList.initWithNiwsConfig(config);
		return serverList;
	}

	@Bean
	@ConditionalOnMissingBean
	public ServerListUpdater ribbonServerListUpdater(IClientConfig config) {
		return new PollingServerListUpdater(config);
	}

	/**
	2018/8/1-22:36白初心iceu ILoadBalancer的bean
	 ZoneAwareLoadBalancer 默认的Balancer
	 在ZoneAwareLoadBalancer中没有看到有相关的ServerList
	 跟到 ZoneAwareLoadBalancer的父类 DynamicServerListLoadBalancer

	 ZoneAwareLoadBalancer<T extends Server> extends DynamicServerListLoadBalancer<T>


	 public DynamicServerListLoadBalancer(IClientConfig clientConfig, IRule rule, IPing ping,
	 ServerList<T> serverList, ServerListFilter<T> filter,
	 ServerListUpdater serverListUpdater) {
	 super(clientConfig, rule, ping);
	 this.serverListImpl = serverList;
	 this.filter = filter;
	 this.serverListUpdater = serverListUpdater;
	 if (filter instanceof AbstractServerListFilter) {
	 ((AbstractServerListFilter) filter).setLoadBalancerStats(getLoadBalancerStats());
	 }
	 restOfInit(clientConfig);
	 }

	 void restOfInit(IClientConfig clientConfig) {
	 boolean primeConnection = this.isEnablePrimingConnections();
	 // turn this off to avoid duplicated asynchronous priming done in BaseLoadBalancer.setServerList()
	 this.setEnablePrimingConnections(false);

	 启用和初始化学习新的server的能力 如果serviceAble有新的实例加进来了 是不是可以在这里感知到那些新加入进来的
	 服务实例
	 enableAndInitLearnNewServersFeature();

	 更新服务实例 可能就是在创建ZoneAwareLoadBalancer<实例的时候
	 通过调用他的父类DynamicServerListLoadBalancer 的的构造函数 调用了restInit犯法
	 调用了updatelistOfServers()方法 通过这个方法 从eureka client那里获取到ServiceAble的server lisy
	 updateListOfServers();
	 if (primeConnection && this.getPrimeConnections() != null) {
	 this.getPrimeConnections()
	 .primeConnections(getReachableServers());
	 }
	 this.setEnablePrimingConnections(primeConnection);
	 LOGGER.info("DynamicServerListLoadBalancer for client {} initialized: {}", clientConfig.getClientName(), this.toString());
	 }




	*/

	/**
	2018/8/2-7:38白初心iceu


	 serverListImpl 去eureka获取服务列表
	 volatile ServerList<T> serverListImpl;

	 发现是在构造ZoneAwareLoadBalancer的时候从构造函数里面传进来的
	 那么就得回到RibbonClientConfiguration那儿找一下 传递了什么serverList
	 本身 是从@bean方法入参传入进来的  在某个XXXAutoConfiguration或者XXXConfiguration里面
	 实例化了一个serverList的bean 才可以在这里传入进来


	 @VisibleForTesting
	 public void updateListOfServers() {
	 List<T> servers = new ArrayList<T>();
	 if (serverListImpl != null) {
	 servers = serverListImpl.getUpdatedListOfServers();
	 LOGGER.debug("List of Servers for {} obtained from Discovery client: {}",
	 getIdentifier(), servers);

	 if (filter != null) {
	 servers = filter.getFilteredListOfServers(servers);
	 LOGGER.debug("Filtered List of Servers for {} obtained from Discovery client: {}",
	 getIdentifier(), servers);
	 }
	 }
	 updateAllServerList(servers);
	 }
	 */
	@Bean
	@ConditionalOnMissingBean
	public ILoadBalancer ribbonLoadBalancer(IClientConfig config,
			ServerList<Server> serverList, ServerListFilter<Server> serverListFilter,
			IRule rule, IPing ping, ServerListUpdater serverListUpdater) {
		if (this.propertiesFactory.isSet(ILoadBalancer.class, name)) {
			return this.propertiesFactory.get(ILoadBalancer.class, config, name);
		}
		return new ZoneAwareLoadBalancer<>(config, rule, ping, serverList,
				serverListFilter, serverListUpdater);
	}

	@Bean
	@ConditionalOnMissingBean
	@SuppressWarnings("unchecked")
	public ServerListFilter<Server> ribbonServerListFilter(IClientConfig config) {
		if (this.propertiesFactory.isSet(ServerListFilter.class, name)) {
			return this.propertiesFactory.get(ServerListFilter.class, config, name);
		}
		ZonePreferenceServerListFilter filter = new ZonePreferenceServerListFilter();
		filter.initWithNiwsConfig(config);
		return filter;
	}

	@Bean
	@ConditionalOnMissingBean
	public RibbonLoadBalancerContext ribbonLoadBalancerContext(ILoadBalancer loadBalancer,
			IClientConfig config, RetryHandler retryHandler) {
		return new RibbonLoadBalancerContext(loadBalancer, config, retryHandler);
	}

	@Bean
	@ConditionalOnMissingBean
	public RetryHandler retryHandler(IClientConfig config) {
		return new DefaultLoadBalancerRetryHandler(config);
	}

	@Bean
	@ConditionalOnMissingBean
	public ServerIntrospector serverIntrospector() {
		return new DefaultServerIntrospector();
	}

	@PostConstruct
	public void preprocess() {
		setRibbonProperty(name, DeploymentContextBasedVipAddresses.key(), name);
	}

	static class OverrideRestClient extends RestClient {

		private IClientConfig config;
		private ServerIntrospector serverIntrospector;

		protected OverrideRestClient(IClientConfig config,
				ServerIntrospector serverIntrospector) {
			super();
			this.config = config;
			this.serverIntrospector = serverIntrospector;
			initWithNiwsConfig(this.config);
		}

		@Override
		public URI reconstructURIWithServer(Server server, URI original) {
			URI uri = updateToHttpsIfNeeded(original, this.config,
					this.serverIntrospector, server);
			return super.reconstructURIWithServer(server, uri);
		}

		@Override
		protected Client apacheHttpClientSpecificInitialization() {
			ApacheHttpClient4 apache = (ApacheHttpClient4) super.apacheHttpClientSpecificInitialization();
			apache.getClientHandler().getHttpClient().getParams().setParameter(
					ClientPNames.COOKIE_POLICY, CookiePolicy.IGNORE_COOKIES);
			return apache;
		}

	}

}
