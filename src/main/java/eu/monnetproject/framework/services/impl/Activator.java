/**
 * ********************************************************************************
 * Copyright (c) 2011, Monnet Project All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met: *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer. * Redistributions in binary
 * form must reproduce the above copyright notice, this list of conditions and
 * the following disclaimer in the documentation and/or other materials provided
 * with the distribution. * Neither the name of the Monnet Project nor the names
 * of its contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE MONNET PROJECT BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * *******************************************************************************
 */
package eu.monnetproject.framework.services.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import org.osgi.framework.*;

/**
 *
 * @author John McCrae
 */
public class Activator implements BundleActivator {

    private final HashMap<String, List<OSGiComponent<?>>> components = new HashMap<String, List<OSGiComponent<?>>>();
    private final boolean verbose = Boolean.parseBoolean(System.getProperty("eu.monnetproject.framework.services.verbose", "false"));

    @Override
    public void start(BundleContext bc) throws Exception {
        if (verbose) {
            System.err.println("Starting eu.monnetproject.framework.services");
        }
        final HashSet<String> processedBundles = new HashSet<String>();
        bc.addBundleListener(
                new BundleListener() {

                    @Override
                    public void bundleChanged(BundleEvent be) {
                        final Bundle bundle = be.getBundle();
                        if (be.getType() == BundleEvent.STARTED) {
                            startBundle(bundle, processedBundles);
                        } else if (be.getType() == BundleEvent.STOPPED) {
                            // We don't synchronize as ServiceTracker will do it for us :)
                            stopComponents(bundle);
                        }
                    }
                });
        for (Bundle bundle : bc.getBundles()) {
            if (bundle.getState() == Bundle.STARTING || bundle.getState() == Bundle.ACTIVE) {
                startBundle(bundle, processedBundles);
            }
        }
    }

    private boolean doResolve(Bundle bundle, final LinkedList<OSGiComponent<?>> bundleComps, String pathPrefix, boolean independent) {
        @SuppressWarnings("unchecked")
        final Enumeration<String> entryPaths = bundle.getEntryPaths("/" + pathPrefix);
        if (entryPaths == null) {
            return true;
        }
        while (entryPaths.hasMoreElements()) {
            String entryPath = entryPaths.nextElement();
            if (verbose) {
                System.err.println("Found declaration at " + entryPath);
            }

            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new InputStreamReader(bundle.getResource(entryPath).openStream()));
                final String className = entryPath.substring(pathPrefix.length());
                final Class<?> serviceClass = bundle.loadClass(className);
                String s;
                while ((s = reader.readLine()) != null) {

                    try {
                        if (!s.matches("\\s*")) {
                            if (verbose) {
                                System.err.println("Registering service implementation " + s);
                            }
                            // Register the service
                            final Class<?> implClass = bundle.loadClass(s);
                            mkOSGiComp(implClass, independent, serviceClass, bundle, bundleComps);
                        }
                    } catch (Exception x) {
                        if (verbose) {
                            x.printStackTrace();
                        }
                    }
                }
            } catch (ClassNotFoundException x) {
                System.err.println("Bad service declaration: " + x.getMessage());
                if (verbose) {
                    x.printStackTrace();
                }
            } catch (IOException x) {
                if (verbose) {
                    x.printStackTrace();
                }
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (Exception x) {
                    }
                }
            }
        }
        return false;
    }

    private <C,D> void mkOSGiComp(final Class<D> implClass, boolean independent, final Class<C> serviceClass, Bundle bundle, final LinkedList<OSGiComponent<?>> bundleComps) {
        final InjectableClass<D> injectableClass = new InjectableClass<D>(implClass);
        if (!independent || injectableClass.dependencies().length == 0) {
            @SuppressWarnings("unchecked")
            final OSGiComponent<C> osgiComp = new OSGiComponent<C>((InjectableClass<C>)injectableClass, serviceClass, bundle.getBundleContext());
            osgiComp.start();
            bundleComps.add(osgiComp);
        }
    }

    private void startBundle(final Bundle bundle, Set<String> processedBundles) {
        boolean cont = false;
        final String bundleName = bundle.getSymbolicName() + "-" + bundle.getVersion();
        if (verbose) {
            System.err.println("Processing Bundle: " + bundleName);
        }
        synchronized (processedBundles) {
            if (!processedBundles.contains(bundleName)) {
                processedBundles.add(bundleName);
                cont = true;
            }
        }
        if (cont) {
            startComponents(bundle);
        }
    }

    private void startComponents(Bundle bundle) {
        final String bundleName = bundle.getSymbolicName() + "-" + bundle.getVersion();
        final LinkedList<OSGiComponent<?>> bundleComps = new LinkedList<OSGiComponent<?>>();
        components.put(bundleName, bundleComps);
        doResolve(bundle, bundleComps,StdResolver.PATH_PREFIX,false);
        doResolve(bundle, bundleComps,StdResolver.JSL_PATH_PREFIX,true);
    }

    private void stopComponents(Bundle bundle) {
        final String bundleName = bundle.getSymbolicName() + "-" + bundle.getVersion();
        if (verbose) {
            System.err.println("Stopping bundle " + bundleName);
        }
        if (components.containsKey(bundleName)) {
            for (OSGiComponent<?> component : components.get(bundleName)) {
                component.stop();
            }
        }
        components.remove(bundleName);
    }

    @Override
    public void stop(BundleContext bc) throws Exception {
        for (List<OSGiComponent<?>> compList : components.values()) {
            for (OSGiComponent<?> component : compList) {
                component.stop();
            }
        }
    }
}
