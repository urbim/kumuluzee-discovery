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

import com.kumuluz.ee.discovery.utils.ConsulService;
import com.kumuluz.ee.discovery.utils.ConsulServiceConfiguration;
import com.orbitz.consul.AgentClient;
import com.orbitz.consul.NotRegisteredException;

import java.util.logging.Logger;

/**
 * Runnable for service registration and heartbeats
 *
 * @author Jan Meznarič, Urban Malc
 */
public class ConsulRegistrator implements Runnable {
    private static final Logger log = Logger.getLogger(ConsulRegistrator.class.getName());

    private AgentClient agentClient;
    private ConsulServiceConfiguration serviceConfiguration;

    private boolean isRegistered;

    public ConsulRegistrator(AgentClient agentClient, ConsulServiceConfiguration serviceConfiguration) {
        this.agentClient = agentClient;
        this.serviceConfiguration = serviceConfiguration;

        this.isRegistered = false;
    }

    @Override
    public void run() {
        if(!this.isRegistered) {
            this.registerToConsul();
        }

        log.info("Sending heartbeat.");
        try {
            agentClient.pass(this.serviceConfiguration.getServiceId());
        } catch (NotRegisteredException e) {
            log.warning("Received NotRegisteredException from Consul AgentClient. Reregistering service.");
            this.isRegistered = false;
            this.registerToConsul();
            // TODO check
        }
    }

    private void registerToConsul() {
        log.info("Registering service with Consul. Service name: " + this.serviceConfiguration.getServiceName() +
                " Service ID: " + this.serviceConfiguration.getServiceId());

        if(agentClient != null) {
            agentClient.register(this.serviceConfiguration.getServicePort(), this.serviceConfiguration.getTtl(),
                    this.serviceConfiguration.getServiceConsulKey(), this.serviceConfiguration.getServiceId(),
                    this.serviceConfiguration.getServiceProtocol(),
                    ConsulService.TAG_VERSION_PREFIX + this.serviceConfiguration.getVersion());

            this.isRegistered = true;
        } else {
            log.severe("Consul not initialized.");
        }
    }
}