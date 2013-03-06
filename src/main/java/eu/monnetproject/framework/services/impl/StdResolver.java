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

import eu.monnetproject.framework.services.ServiceCollection;
import eu.monnetproject.framework.services.ServiceLoadException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.*;
import org.osgi.framework.Bundle;

/**
 * Default method for resolving services. Used if OSGi is not present
 *
 * @author John McCrae
 */
public class StdResolver {

    public final static String PATH_PREFIX = System.getProperty("eu.monnetproject.framework.services.path", "META-INF/components/");
    public final static String JSL_PATH_PREFIX = System.getProperty("eu.monnetproject.framework.services.jslpath", "META-INF/services/");
    public final static boolean noOSGi = Boolean.parseBoolean(System.getProperty("eu.monnetproject.framework.services.osgi", "false"));
    private static final boolean verbose = Boolean.parseBoolean(System.getProperty("eu.monnetproject.framework.services.verbose", "false"));

    private StdResolver() {
    }

    public static <S> S resolveImmediate(Class<S> serviceClass) {
        ServiceLoadException lastException = null;
        if (OSGiUtil.getFrameWorkBundle() != null && !noOSGi) {
            // OSGi class path method
            final Bundle[] bundles = OSGiUtil.getFrameWorkBundle().getBundleContext().getBundles();
            for (Bundle bundle : bundles) {
                final URL resource = bundle.getResource("/" + PATH_PREFIX + serviceClass.getName());
                try {
                    if (resource != null) {
                        return resolveFirstURL(serviceClass, resource, false, bundle);
                    }
                } catch (ServiceLoadException x) {
                    lastException = x;
                }
                final URL jslResource = bundle.getResource("/" + JSL_PATH_PREFIX + serviceClass.getName());
                try {
                    if (jslResource != null) {
                        return resolveFirstURL(serviceClass, jslResource, true, bundle);
                    }
                } catch (ServiceLoadException x) {
                    lastException = x;
                }
            }
        }
        // Non-OSGi class path method
        try {
            final Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(PATH_PREFIX + serviceClass.getName());
            while (resources.hasMoreElements()) {
                try {
                    return resolveFirstURL(serviceClass, resources.nextElement(), false, null);
                } catch (ServiceLoadException x) {
                    lastException = x;
                }
            }
        } catch (IOException x) {
            lastException = new ServiceLoadException(serviceClass, x);
        }
        try {
            final Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(JSL_PATH_PREFIX + serviceClass.getName());
            while (resources.hasMoreElements()) {
                try {
                    return resolveFirstURL(serviceClass, resources.nextElement(), true, null);
                } catch (ServiceLoadException x) {
                    lastException = x;
                }
            }
        } catch (IOException x) {
            lastException = new ServiceLoadException(serviceClass, x);
        }
        if (lastException != null) {
            throw lastException;
        } else {
            if (verbose) {
                System.err.println("No candidate service for " + serviceClass.getName());
            }
            throw new ServiceLoadException(serviceClass);
        }
    }

    private static <S> S resolveFirstURL(Class<S> serviceClass, URL url, boolean independent, Bundle bundle) {
        try {
            final BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
            String s;
            ServiceLoadException lastException = null;
            while ((s = reader.readLine()) != null) {
                final String[] ss = s.split(";");
                try {
                    @SuppressWarnings("unchecked")
                    Class<S> c = bundle == null ? (Class<S>) Thread.currentThread().getContextClassLoader().loadClass(ss[0])
                            : bundle.loadClass(ss[0]);
                    try {
                        if (verbose) {
                            System.err.println("Binding " + ss[0] + " as " + serviceClass.getName());
                        }
                        return resolveSingle(serviceClass, c, independent);
                    } catch (ServiceLoadException x) {
                        lastException = x;
                    }
                } catch (ClassNotFoundException x) {
                    if (verbose) {
                        System.err.println("Failed to load class " + ss[0] + ": " + x.getMessage());
                    }
                    throw new ServiceLoadException(serviceClass, x);
                }
            }
            if (lastException != null) {
                throw lastException;
            } else {
                if (verbose) {
                    System.err.println("Empty service descriptor @ " + url);
                }
                throw new ServiceLoadException(serviceClass, "Empty service declaration @ " + url);
            }
        } catch (IOException ex) {
            if (verbose) {
                System.err.println("Error reading service descriptor " + url.toString() + ": " + ex.getMessage());
            }
            throw new ServiceLoadException(serviceClass, ex);
        }
    }

    private static <S> LinkedList<S> resolveURL(Class<S> serviceClass, URL url, boolean independent, Bundle bundle) {
        LinkedList<S> services = new LinkedList<S>();
        try {
            final BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
            String s;
            ServiceLoadException lastException = null;
            while ((s = reader.readLine()) != null) {
                final String[] ss = s.split(";");
                if (ss[0].matches("\\s*")) {
                    continue;
                }
                try {
                    @SuppressWarnings("unchecked")
                    Class<S> c = bundle == null ? (Class<S>) Thread.currentThread().getContextClassLoader().loadClass(ss[0])
                            : bundle.loadClass(ss[0]);
                    try {
                        if (verbose) {
                            System.err.println("Binding " + ss[0] + " as " + serviceClass.getName());
                        }
                        services.add(resolveSingle(serviceClass, c, independent));
                    } catch (ServiceLoadException x) {
                        if (verbose) {
                            System.err.println("Service not loaded as " + x.getClass().getName() + ": " + x.getMessage());
                        }
                        lastException = x;
                    }
                } catch (ClassNotFoundException x) {
                    if (verbose) {
                        System.err.println("Failed to load class " + ss[0] + ": " + x.getMessage());
                    }
                    throw new ServiceLoadException(serviceClass, x);
                }
            }
            if (!services.isEmpty()) {
                return services;
            } else if (lastException != null) {
                throw lastException;
            } else {
                return new LinkedList<S>();
            }
        } catch (IOException ex) {
            if (verbose) {
                System.err.println("Error reading service descriptor " + url.toString() + ": " + ex.getMessage());
            }
            throw new ServiceLoadException(serviceClass, ex);
        }
    }

    private static <S, T extends S> S resolveSingle(Class<S> serviceClass, Class<T> implClass, boolean independent) {

        final InjectableClass<T> injectableClass = new InjectableClass<T>(implClass);
        if (independent && injectableClass.dependencies().length != 0) {
            if (verbose) {
                System.err.println(implClass.getName() + " does not have a single public no-args constructor");
            }
            throw new ServiceLoadException(implClass, "Class does not have a single public no-args constructor");
        }
        Object[] arguments = new Object[injectableClass.dependencies().length];
        int i = 0;
        for (Type type : injectableClass.dependencies()) {
            //if (type instanceof ParameterizedType && ((Class<?>) ((ParameterizedType) type).getRawType()).isAssignableFrom(ServiceCollection.class)) {
            if (InjectableClass.isMultiple(type)) {
                Class<?> clazz = (Class<?>) ((ParameterizedType) type).getActualTypeArguments()[0];
                try {
                    arguments[i] = resolveExtant(clazz, injectableClass.isNonEmpty()[i]);
                } catch (ServiceLoadException x) {
                    if (verbose) {
                        System.err.println("Failed to bind argument " + i + " of " + implClass.getName());
                    }
                    throw new ServiceLoadException(x, implClass);
                }
            } else {
                try {
                    Class<?> clazz;
                    if (type instanceof Class) {
                        clazz = (Class) type;
                    } else {
                        clazz = (Class) ((ParameterizedType) type).getRawType();
                    }
                    arguments[i] = resolveImmediate(clazz);
                } catch (ServiceLoadException x) {

                    if (verbose) {
                        System.err.println("Failed to bind argument " + i + " of " + implClass.getName());
                    }
                    throw new ServiceLoadException(x, implClass);
                }
            }
            i++;
        }
        return injectableClass.newInstance(arguments);
    }

    public static <S> ServiceCollection<S> resolveExtant(Class<S> serviceClass) {
        return resolveExtant(serviceClass, false);
    }

    public static <S> ServiceCollection<S> resolveExtant(Class<S> serviceClass, boolean nonEmpty) {
        final ServiceCollectionImpl<S> services = new ServiceCollectionImpl<S>();
        ServiceLoadException lastException = null;
        if (OSGiUtil.getFrameWorkBundle() != null && !noOSGi) {
            final Bundle[] bundles = OSGiUtil.getFrameWorkBundle().getBundleContext().getBundles();
            for (Bundle bundle : bundles) {
                try {
                    final URL resource = bundle.getResource("/" + PATH_PREFIX + serviceClass.getName());
                    if (resource != null) {
                        services.addAll(resolveURL(serviceClass, resource, false, bundle));
                    }
                } catch (ServiceLoadException x) {
                    lastException = x;
                }
                try {
                    final URL resource = bundle.getResource("/" + JSL_PATH_PREFIX + serviceClass.getName());
                    if (resource != null) {
                        services.addAll(resolveURL(serviceClass, resource, true, bundle));
                    }
                } catch (ServiceLoadException x) {
                    lastException = x;
                }
            }
        }
        try {
            final Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(PATH_PREFIX + serviceClass.getName());
            while (resources.hasMoreElements()) {
                try {
                    services.addAll(resolveURL(serviceClass, resources.nextElement(), false, null));
                } catch (ServiceLoadException x) {
                    lastException = x;
                }
            }
        } catch (IOException x) {
            lastException = new ServiceLoadException(serviceClass, x);
        }
        try {
            final Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(JSL_PATH_PREFIX + serviceClass.getName());
            while (resources.hasMoreElements()) {
                try {
                    services.addAll(resolveURL(serviceClass, resources.nextElement(), true, null));
                } catch (ServiceLoadException x) {
                    lastException = x;
                }
            }
        } catch (IOException x) {
            lastException = new ServiceLoadException(serviceClass, x);
        }

        if (!services.isEmpty() || (!nonEmpty && lastException == null)) {
            return services;
        } else if (lastException != null) {
            throw lastException;
        } else {
            if (verbose) {
                System.err.println("Could not load non-empty list of services for " + serviceClass);
            }
            throw new ServiceLoadException(serviceClass, "Could not load non-empty list of services");
        }
    }

    private static class ServiceCollectionImpl<S> extends LinkedList<S> implements ServiceCollection<S> {

        private static final long serialVersionUID = 1L;

        @Override
        public ServiceIterator<S> iterator() {
            return new ServiceIteratorImpl();
        }

        private class ServiceIteratorImpl implements ServiceIterator<S> {

            private final Iterator<S> iterator = ServiceCollectionImpl.super.iterator();

            @Override
            @SuppressWarnings("unchecked")
            public Map<String, Object> props() {
                return Collections.EMPTY_MAP;
            }

            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public S next() {
                return iterator.next();
            }

            @Override
            public void remove() {
                iterator.remove();
            }
        }
    }
}
