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
import com.kumuluz.ee.discovery.utils.ConsulServiceConfiguration;
import com.kumuluz.ee.discovery.utils.DiscoveryUtil;
import com.orbitz.consul.AgentClient;
import com.orbitz.consul.Consul;
import com.orbitz.consul.ConsulException;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
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

    private static final int CONSUL_WATCH_WAIT_SECONDS = 120;

    private Consul consul;
    private AgentClient agentClient;

    @PostConstruct
    public void init() {

        this.registeredServices = new LinkedList<>();

        consul = Consul.builder()
                .withPing(false)
                .withReadTimeoutMillis(CONSUL_WATCH_WAIT_SECONDS*1000 + (CONSUL_WATCH_WAIT_SECONDS*1000) / 16 + 1000)
                .build();

        try {
            consul.agentClient().ping();
        } catch (ConsulException e) {
            log.severe("Cannot ping consul agent: " + e.getLocalizedMessage());
        }

        agentClient = consul.agentClient();
    }

    @Override
    public void register(String serviceName, String version, String environment, long ttl, long pingInterval,
                         boolean singleton) {

        String serviceProtocol = configurationUtil.get("kumuluzee.discovery.consul.protocol").orElse("http");
        int servicePort = configurationUtil.getInteger("port").orElse(8080);

        ConsulServiceConfiguration serviceConfiguration = new ConsulServiceConfiguration(serviceName, serviceProtocol,
                servicePort, ttl);

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
        return null;
    }

    @Override
    public Optional<URL> getServiceInstance(String serviceName, String version, String environment) {
        return null;
    }

    @Override
    public Optional<List<String>> getServiceVersions(String serviceName, String environment) {
        return null;
    }

    @Override
    public void disableServiceInstance(String serviceName, String version, String environment, URL url) {

    }
}
