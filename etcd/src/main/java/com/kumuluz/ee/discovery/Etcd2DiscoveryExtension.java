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

import com.kumuluz.ee.common.Extension;
import com.kumuluz.ee.common.config.EeConfig;
import com.kumuluz.ee.common.dependencies.*;
import com.kumuluz.ee.common.wrapper.KumuluzServerWrapper;

import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * KumuluzEE framework extension for etcd-based service discovery
 *
 * @author Jan Meznarič
 */
@EeExtensionDef(name = "Service discovery with etcd API v2", type = EeExtensionType.DISCOVERY)
@EeComponentDependency(EeComponentType.CDI)
public class Etcd2DiscoveryExtension implements Extension {

    private static final Logger log = Logger.getLogger(Etcd2DiscoveryExtension.class.getName());

    @Override
    public void init(KumuluzServerWrapper kumuluzServerWrapper, EeConfig eeConfig) {

        log.info("Initialising service discovery.");

    }

    @Override
    public void load() {

    }

    @Override
    public <T> Optional<T> getProperty(Class<T> aClass) {

        return null;

    }

    @Override
    public <T> Optional<T> getProperty(Class<T> aClass, String s) {
        return null;
    }

    @Override
    public <T> Optional<List<T>> getProperties(Class<T> aClass) {
        return null;
    }

    @Override
    public <T> Optional<List<T>> getProperties(Class<T> aClass, String s) {
        return null;
    }
}