/****************************************************************************
 * Copyright (c) 2011, Monnet Project
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Monnet Project nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE MONNET PROJECT BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ********************************************************************************/
package eu.monnetproject.framework.services.impl;

import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

/**
 * Utility class for OSGi
 * @author John McCrae
 */
public class OSGiUtil extends SecurityManager {
    /**
     * Get the framework bundle.
     * @return The framework bundle or null if there is no OSGi running.
     */
    public static Bundle getFrameWorkBundle() {
        final OSGiUtil osgiUtil = new OSGiUtil();
        for (Class<?> c : osgiUtil.getClassContext()) {
            final Bundle bundle = FrameworkUtil.getBundle(c);
            if (bundle != null && (bundle.getState() & (Bundle.ACTIVE | Bundle.STARTING | Bundle.STOPPING)) > 0) {
                return bundle.getBundleContext().getBundles()[0];
            }
        }
        return null;
    }
    
    /**
     * Get the bundle that called this function
     * @return The bundle or null if there is no OSGi running
     */
    public static Bundle getCallingBundle() {
        final OSGiUtil osgiUtil = new OSGiUtil();
        for (Class<?> c : osgiUtil.getClassContext()) {
            final Bundle bundle = FrameworkUtil.getBundle(c);
            if (bundle != null && !bundle.getSymbolicName().equals("eu.monnetproject.core")
                    && !bundle.getSymbolicName().equals("eu.monnetproject.framework.services")) {
                return bundle;
            }
        }
        return null;
    }
}
