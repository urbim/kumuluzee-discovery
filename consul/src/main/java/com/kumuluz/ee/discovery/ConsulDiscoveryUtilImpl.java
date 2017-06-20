/*
 *  Copyright (c) 2014-2017 Kumuluz and/or its affiliates
 *  and other contributors as indicated by the @author tags and
 *  the contributor list.
 *
 *  Licensed under the MIT License (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  https://opensource.org/licenses/MIT
 *
 *  The software is provided "AS IS", WITHOUT WARRANTY OF ANY KIND, express or
 *  implied, including but not limited to the warranties of merchantability,
 *  fitness for a particular purpose and noninfringement. in no event shall the
 *  authors or copyright holders be liable for any claim, damages or other
 *  liability, whether in an action of contract, tort or otherwise, arising from,
 *  out of or in connection with the software or the use or other dealings in the
 *  software. See the License for the specific language governing permissions and
 *  limitations under the License.
*/
package com.kumuluz.ee.discovery;

import com.kumuluz.ee.configuration.utils.ConfigurationUtil;
import com.kumuluz.ee.discovery.utils.ConsulService;
import com.kumuluz.ee.discovery.utils.ConsulServiceConfiguration;
import com.kumuluz.ee.discovery.utils.ConsulUtils;
import com.kumuluz.ee.discovery.utils.DiscoveryUtil;
import com.orbitz.consul.AgentClient;
import com.orbitz.consul.Consul;
import com.orbitz.consul.ConsulException;
import com.orbitz.consul.HealthClient;
import com.orbitz.consul.cache.ConsulCache;
import com.orbitz.consul.cache.ServiceHealthCache;
import com.orbitz.consul.cache.ServiceHealthKey;
import com.orbitz.consul.model.health.ServiceHealth;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * @author Jan Meznarič, Urban Malc
 */
@ApplicationScoped
public class ConsulDiscoveryUtilImpl implements DiscoveryUtil {

    private static final Logger log = Logger.getLogger(ConsulDiscoveryUtilImpl.class.getName());
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final ConfigurationUtil configurationUtil = ConfigurationUtil.getInstance();

    private List<ConsulServiceConfiguration> registeredServices;

    private Map<String, List<ConsulService>> serviceInstances;
    private int lastInstanceServedIndex;

    private static final int CONSUL_WATCH_WAIT_SECONDS = 120;

    private AgentClient agentClient;
    private HealthClient healthClient;

    @PostConstruct
    public void init() {

        this.registeredServices = new LinkedList<>();

        this.serviceInstances = new HashMap<>();

        Consul consul = Consul.builder()
                .withPing(false)
                .withReadTimeoutMillis(CONSUL_WATCH_WAIT_SECONDS*1000 + (CONSUL_WATCH_WAIT_SECONDS*1000) / 16 + 1000)
                .build();

        try {
            consul.agentClient().ping();
        } catch (ConsulException e) {
            log.severe("Cannot ping consul agent: " + e.getLocalizedMessage());
        }

        this.agentClient = consul.agentClient();
        this.healthClient = consul.healthClient();
    }

    @Override
    public void register(String serviceName, String version, String environment, long ttl, long pingInterval,
                         boolean singleton) {

        String serviceProtocol = configurationUtil.get("kumuluzee.discovery.consul.protocol").orElse("http");
        int servicePort = configurationUtil.getInteger("port").orElse(8080);

        ConsulServiceConfiguration serviceConfiguration = new ConsulServiceConfiguration(serviceName, environment,
                version, serviceProtocol, servicePort, ttl);

        // register and schedule heartbeats
        ConsulRegistrator registrator = new ConsulRegistrator(this.agentClient, serviceConfiguration);
        scheduler.scheduleWithFixedDelay(registrator, 0, pingInterval, TimeUnit.SECONDS);

        this.registeredServices.add(serviceConfiguration);
    }

    @Override
    public void deregister() {
        if(agentClient != null) {
            for(ConsulServiceConfiguration serviceConfiguration : registeredServices) {
                log.info("Deregistering service with Consul. Service name: " +
                        serviceConfiguration.getServiceName() + " Service ID: " + serviceConfiguration.getServiceId());
                agentClient.deregister(serviceConfiguration.getServiceId());
            }
        }
    }

    @Override
    public Optional<List<URL>> getServiceInstances(String serviceName, String version, String environment) {
        String consulServiceKey = ConsulUtils.getConsulServiceKey(serviceName, environment);
        if (!this.serviceInstances.containsKey(consulServiceKey)) {

            log.info("Performing service lookup on Consul Agent.");

            List<ServiceHealth> serviceInstances = healthClient.getHealthyServiceInstances(consulServiceKey)
                    .getResponse();

            List<ConsulService> serviceUrls = new ArrayList<>();
            for (ServiceHealth serviceHealth : serviceInstances) {
                ConsulService consulService = ConsulService.getInstanceFromServiceHealth(serviceHealth);
                if(consulService != null) {
                    serviceUrls.add(consulService);
                }
            }

            this.serviceInstances.put(consulServiceKey, serviceUrls);

            addServiceListener(consulServiceKey);

            return Optional.of(filterCorrectVersions(serviceUrls, version));
        } else {
            return Optional.of(filterCorrectVersions(this.serviceInstances.get(consulServiceKey), version));
        }
    }

    private List<URL> filterCorrectVersions(List<ConsulService> serviceList, String version) {
        List<URL> urlList = new LinkedList<>();

        for(ConsulService consulService : serviceList) {
            if(consulService.getVersion().equals(version)) { //TODO npm
                urlList.add(consulService.getServiceUrl());
            }
        }

        return urlList;
    }

    @Override
    public Optional<URL> getServiceInstance(String serviceName, String version, String environment) {
        Optional<List<URL>> optionalServiceInstances = getServiceInstances(serviceName, version, environment);

        if (optionalServiceInstances.isPresent()) {

            List<URL> serviceInstances = optionalServiceInstances.get();

            if (!serviceInstances.isEmpty()) {
                int index = 0;
                if (serviceInstances.size() >= lastInstanceServedIndex + 2) {
                    index = lastInstanceServedIndex + 1;
                }
                lastInstanceServedIndex = index;

                return Optional.of(serviceInstances.get(index));
            }
        }

        return Optional.empty();
    }

    @Override
    public Optional<List<String>> getServiceVersions(String serviceName, String environment) {
        return null;
    }

    private void addServiceListener(String serviceKey) {
        ServiceHealthCache svHealth = ServiceHealthCache.newCache(healthClient, serviceKey);

        svHealth.addListener(new ConsulCache.Listener<ServiceHealthKey, ServiceHealth>() {

            @Override
            public void notify(Map<ServiceHealthKey, ServiceHealth> newValues) {

                log.info("Service instances for service " + serviceKey + " refreshed.");

                serviceInstances.get(serviceKey).clear();

                for (Map.Entry<ServiceHealthKey, ServiceHealth> serviceHealthKey : newValues.entrySet()) {
                    ConsulService consulService = ConsulService
                            .getInstanceFromServiceHealth(serviceHealthKey.getValue());
                    if(consulService != null) {
                        serviceInstances.get(serviceKey).add(consulService);
                    }
                }
            }
        });

        try {
            svHealth.start();
        } catch (Exception e) {
            log.severe("Cannot start service listener: " + e.getLocalizedMessage());
        }

    }

    @Override
    public void disableServiceInstance(String serviceName, String version, String environment, URL url) {

    }
}