/*
 * Copyright 2013-2017 the original author or authors.
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

import java.lang.reflect.Constructor;

import org.springframework.beans.BeanUtils;
import org.springframework.cloud.context.named.NamedContextFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.netflix.client.IClient;
import com.netflix.client.IClientConfigAware;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.ILoadBalancer;

/**
 * A factory that creates client, load balancer and client configuration instances. It
 * creates a Spring ApplicationContext per client name, and extracts the beans that it
 * needs from there.
 *
 * @author Spencer Gibb
 * @author Dave Syer
 */
/**
2018/8/18:58白初心iceu
 对spring进行了封装 从spring里面获取bean的入口 都变成了这个
 对每一个服务 都对应着服务名称  都对应着spring的一个applicationContext 容器
 ServiceAble对应着自己的独立的一个spring容器
 如果要获取ServiceA服务的LoadBalancer  那么就从ServiceAble服务对应的自己的ApplicationContext
 容器中去获取自己的LoadBalancer即可

 在SpringClientFactory 一个服务 比如serviceAble 对应着一个独立的ApplicationContext 里面包含了
 自己这个服务的独立的一堆组件 比如Loadbalancer
*/
public class SpringClientFactory extends NamedContextFactory<RibbonClientSpecification> {

	static final String NAMESPACE = "ribbon";

	public SpringClientFactory() {
		super(RibbonClientConfiguration.class, NAMESPACE, "ribbon.client.name");
	}

	/**
	 * Get the rest client associated with the name.
	 * @throws RuntimeException if any error occurs
	 */
	public <C extends IClient<?, ?>> C getClient(String name, Class<C> clientClass) {
		return getInstance(name, clientClass);
	}

	/**
	 * Get the load balancer associated with the name.
	 * @throws RuntimeException if any error occurs
	 */
	/**
	白初心iceu 通过SpringClientFactory来获取对应的LoadBalancer
	 如果要获取LoadBalancer 只要在自己的SpringAppliactiponContext中获取
	 根据LoadBalancer接口的类型即可
	*/
	public ILoadBalancer getLoadBalancer(String name) {
		return getInstance(name, ILoadBalancer.class);
	}

	/**
	 * Get the client config associated with the name.
	 * @throws RuntimeException if any error occurs
	 */
	public IClientConfig getClientConfig(String name) {
		return getInstance(name, IClientConfig.class);
	}

	/**
	 * Get the load balancer context associated with the name.
	 * @throws RuntimeException if any error occurs
	 */
	public RibbonLoadBalancerContext getLoadBalancerContext(String serviceId) {
		return getInstance(serviceId, RibbonLoadBalancerContext.class);
	}

	static <C> C instantiateWithConfig(Class<C> clazz, IClientConfig config) {
		return instantiateWithConfig(null, clazz, config);
	}

	static <C> C instantiateWithConfig(AnnotationConfigApplicationContext context,
										Class<C> clazz, IClientConfig config) {
		C result = null;
		
		try {
			Constructor<C> constructor = clazz.getConstructor(IClientConfig.class);
			result = constructor.newInstance(config);
		} catch (Throwable e) {
			// Ignored
		}
		
		if (result == null) {
			result = BeanUtils.instantiate(clazz);
			
			if (result instanceof IClientConfigAware) {
				((IClientConfigAware) result).initWithNiwsConfig(config);
			}
			
			if (context != null) {
				context.getAutowireCapableBeanFactory().autowireBean(result);
			}
		}
		
		return result;
	}
  /**
  2018/8/1 22:24白初心iceu 通过SpringClientFactory来获取对应的LoadBalancer @{link} RibbonClientConfiguration
  */
	@Override
	public <C> C getInstance(String name, Class<C> type) {
		C instance = super.getInstance(name, type);
		if (instance != null) {
			return instance;
		}
		IClientConfig config = getInstance(name, IClientConfig.class);
		return instantiateWithConfig(getContext(name), type, config);
	}

	@Override
	protected AnnotationConfigApplicationContext getContext(String name) {
		return super.getContext(name);
	}

}

