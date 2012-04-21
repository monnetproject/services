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
import eu.monnetproject.framework.services.ServiceCollection.ServiceIterator;
import java.lang.reflect.Type;
import java.util.*;
import org.osgi.framework.*;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

/**
 * The objects created by the activator that track the state of the system and
 * create appropriate OSGi services
 *
 * @author John McCrae
 */
public class OSGiComponent<C> {

    private final InjectableClass<C> clazz;
    private final Class<C> interfaceClass;
    private final BundleContext context;
    private final ServiceTracker[] trackers;
    private final ServiceReference[] arguments;
    private final ServiceCollection<?>[] collections;
    // satisfied is locked with arguments
    private int satisfied = 0;
    private ServiceRegistration registration;
    // Used to indicate that registration is locked
    private final Object regLock = new Object();
    private static final boolean verbose = Boolean.parseBoolean(System.getProperty("eu.monnetproject.framework.services.verbose", "false"));

    public OSGiComponent(final InjectableClass<C> clazz, final Class<C> interfaceClass, final BundleContext context) {
        this.clazz = clazz;
        this.interfaceClass = interfaceClass;
        this.context = context;
        final Type[] deps = clazz.dependencies();
        this.trackers = new ServiceTracker[deps.length];
        this.arguments = new ServiceReference[deps.length];
        this.collections = new ServiceCollection<?>[deps.length];
    }

    public void start() {
        final Type[] deps = clazz.dependencies();
        if (deps.length == 0) {
            log("Starting immediate service");
            // Start immediately
            final Object newInstance = clazz.newInstance(new Object[0]);
            final Hashtable<Object, Object> props = new Hashtable<Object, Object>();
            props.put("component.name", clazz.getClassName());
            registration = context.registerService(interfaceClass.getName(), newInstance, props);
        } else {
            // Register trackers
            int i = 0;
            for (Type t : deps) {
                if (InjectableClass.isMultiple(t)) {
                    if (clazz.isNonEmpty()[i]) {
                        final Class<?> depClazz = InjectableClass.getRealType(t);
                        trackers[i] = new ServiceTracker(context, depClazz.getName(), new BinjectiveTracker(i));
                        trackers[i].open();
                    } else {
                        final Class<?> depClazz = InjectableClass.getRealType(t);
                        final ServiceCollectionImpl<?> serviceCollectionImpl = new ServiceCollectionImpl<Object>(context);
                        trackers[i] = new ServiceTracker(context, depClazz.getName(), new InjectiveTracker(serviceCollectionImpl));
                        collections[i] = serviceCollectionImpl;
                        trackers[i].open();
                    }
                } else {
                    final Class<?> depClazz = InjectableClass.getRealType(t);
                    trackers[i] = new ServiceTracker(context, depClazz.getName(), new BijectiveTracker(i));
                    trackers[i].open();
                }
                i++;
            }
        }
    }

    public void stop() {
        for (int i = 0; i < trackers.length; i++) {
            try {
                trackers[i].close();
            } catch (Exception x) {
            }
        }
    }

    private void setCollArg(int i, ServiceCollectionImpl<?> coll) {
        ServiceReference[] refs = null;
        ServiceCollection[] colls = null;
        if(verbose) {
            log("Setting arg " + i + " to " + coll + " isEmpty=" + coll.isEmpty());
        }
        synchronized (arguments) {
            if (arguments[i] == null && !coll.isEmpty()) {
                collections[i] = coll;
                satisfied++;
            } else if (arguments[i] != null && coll.isEmpty()) {
                collections[i] = null;
                satisfied--;
            } else {
                return;
            }

            // We have just reached the latching condition
            if (satisfied == arguments.length) {
                refs = new ServiceReference[arguments.length];
                System.arraycopy(arguments, 0, refs, 0, arguments.length);
                colls = new ServiceCollection<?>[collections.length];
                System.arraycopy(collections, 0, colls, 0, collections.length);
            }
        }
        bindArgs(refs, colls);
    }

    private void setArg(int i, ServiceReference sr) {
        ServiceReference[] refs = null;
        ServiceCollection[] colls = null;
        // First we set the arguments object
        synchronized (arguments) {
            // We are removing an argument
            if (sr == null) {
                if (arguments[i] == null) {
                    satisfied--;
                }
                arguments[i] = null;
            } else {
                if (arguments[i] == null) {
                    satisfied++;
                }
                arguments[i] = sr;
                // We have just reached the latching condition
                if (satisfied == arguments.length) {
                    refs = new ServiceReference[arguments.length];
                    System.arraycopy(arguments, 0, refs, 0, arguments.length);
                    colls = new ServiceCollection<?>[collections.length];
                    System.arraycopy(collections, 0, colls, 0, collections.length);
                }
            }
        }

        log("Binding " + sr.toString() + " to " + clazz.getClassName());

        bindArgs(refs, colls);
    }

    private void bindArgs(ServiceReference[] refs, ServiceCollection[] colls) {
        if (refs != null) {
            log(clazz.getClassName() + " is satisifed, starting as factory");
            final ServiceFactoryImpl instance = new ServiceFactoryImpl(refs, colls, context, clazz);
            final Hashtable<Object, Object> props = new Hashtable<Object, Object>();
            props.put("component.name", clazz.getClassName());
            final ServiceRegistration newReg = context.registerService(interfaceClass.getName(), instance, props);
            // oldReg is used to track the old registration, note we do not unregister
            // in the locked code as this could deadlock the system
            ServiceRegistration oldReg = null;
            // Note we only synchronize on the value of the registration
            synchronized (regLock) {
                if (registration != null) {
                    oldReg = registration;
                }
                registration = newReg;
            }
            if (oldReg != null) {
                oldReg.unregister();
            }
        } else {
            ServiceRegistration oldReg = null;
            synchronized (regLock) {
                if (registration != null) {
                    oldReg = registration;
                    registration = null;
                }
            }
            clazz.resetSingleton();
            if (oldReg != null) {
                oldReg.unregister();
            }
        }
    }

    // Track a 1-to-1 dependency
    private class BijectiveTracker implements ServiceTrackerCustomizer {
        private final int i;

        public BijectiveTracker(int i) {
            this.i = i;
        }

        @Override
        public Object addingService(ServiceReference sr) {
            setArg(i, sr);
            return null;
        }

        @Override
        public void modifiedService(ServiceReference sr, Object o) {
            setArg(i, null);
            setArg(i, sr);
        }

        @Override
        public void removedService(ServiceReference sr, Object o) {
            setArg(i, null);
        }
    }

    // Indicates a many-to-1 mapping
    private class InjectiveTracker implements ServiceTrackerCustomizer {
        private final ServiceCollectionImpl<?> collection;

        public InjectiveTracker(ServiceCollectionImpl<?> collection) {
            this.collection = collection;
        }

        @Override
        public Object addingService(ServiceReference sr) {
            collection.add(sr);
            return null;
        }

        @Override
        public void modifiedService(ServiceReference sr, Object o) {
            collection.add(sr);
        }

        @Override
        public void removedService(ServiceReference sr, Object o) {
            collection.remove(sr);
        }
    }

    private class BinjectiveTracker implements ServiceTrackerCustomizer {
        private ServiceCollectionImpl<?> collection;
        private final int i;

        public BinjectiveTracker(int i) {
            this.collection = new ServiceCollectionImpl<Object>(context);
            this.i = i;
        }

        @Override
        public Object addingService(ServiceReference sr) {
            collection.add(sr);
            setCollArg(i, collection);
            return null;
        }

        @Override
        public void modifiedService(ServiceReference sr, Object o) {
            collection.add(sr);
            setCollArg(i, collection);
        }

        @Override
        public void removedService(ServiceReference sr, Object o) {
            collection.remove(sr);
            setCollArg(i, collection);
        }
    }

    private static class ServiceCollectionImpl<D> extends AbstractCollection<D> implements ServiceCollection<D> {
        // We synchronize on this object

        private final HashSet<ServiceReference> objects = new HashSet<ServiceReference>();
        private final BundleContext context;

        public ServiceCollectionImpl(BundleContext context) {
            this.context = context;
        }

        @Override
        public boolean isEmpty() {
            final ServiceIterator<D> iterator = iterator();
            while (iterator.hasNext()) {
                return false;
            }
            return true;
        }

        @Override
        public int size() {
            int n = 0;
            final ServiceIterator<D> iterator = iterator();
            while (iterator.hasNext()) {
                n++;
                iterator.next();
            }
            return n;
        }

        public void add(ServiceReference sr) {
            synchronized (objects) {
                objects.add(sr);
            }
        }

        public void remove(ServiceReference sr) {
            synchronized (objects) {
                objects.remove(sr);
            }
        }

        @Override
        public ServiceIterator<D> iterator() {
            LinkedList<ServiceReference> objectsCopy;
            // Create copy otherwise the collection could be concurrently modified
            // during iteration
            synchronized (objects) {
                objectsCopy = new LinkedList<ServiceReference>(objects);
            }
            return new ServiceIteratorImpl<D>(objectsCopy.iterator(), context);
        }
    }

    private static class ServiceIteratorImpl<D> implements ServiceIterator<D> {

        private final Iterator<ServiceReference> iterator;
        private final BundleContext context;
        private ServiceReference nextRef, lastRef;
        private D next;

        public ServiceIteratorImpl(Iterator<ServiceReference> iterator, BundleContext context) {
            this.iterator = iterator;
            this.context = context;
            advance();
        }

        @SuppressWarnings("unchecked")
        private void advance() {
            lastRef = nextRef;
            while (iterator.hasNext()) {
                nextRef = iterator.next();
                final Object service = context.getService(nextRef);
                if (service != null) {
                    // Yes we unget the service immediately as we have only a transitive reference to it ;)
                    context.ungetService(nextRef);
                    next = (D) service;
                    return;
                }
            }
            next = null;
        }

        @Override
        public Map<String, Object> props() {
            if (lastRef == null) {
                throw new IllegalStateException();
            }
            final HashMap<String, Object> props = new HashMap<String, Object>();
            for (String key : lastRef.getPropertyKeys()) {
                props.put(key, lastRef.getProperty(key));
            }
            return props;
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public D next() {
            if (next == null) {
                throw new NoSuchElementException();
            }
            final D rval = next;
            advance();
            return rval;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
    private static LogService logService;

    private static void log(String message) {
        if (!verbose) {
            return;
        }
        if (logService == null) {
            final Bundle bundle = FrameworkUtil.getBundle(OSGiComponent.class);
            if (bundle != null) {
                final BundleContext context = bundle.getBundleContext();
                if (context != null) {
                    final ServiceReference sr = context.getServiceReference(LogService.class.getName());
                    if (sr != null) {
                        final LogService service = (LogService) context.getService(sr);
                        if (service != null) {
                            logService = service;
                            try {
                                logService.log(0, message);
                            } catch (Exception x) {
                                logService = null;
                                System.err.println(message);
                            }
                        } else {
                            System.err.println(message);
                        }
                    } else {
                        System.err.println(message);
                    }
                } else {
                    System.err.println(message);
                }
            } else {
                System.err.println(message);
            }
        } else {
            try {
                logService.log(0, message);
            } catch (Exception x) {
                logService = null;
                System.err.println(message);
            }
        }
    }

    private static class ServiceFactoryImpl implements ServiceFactory {

        private final ServiceReference[] refs;
        private final ServiceCollection[] colls;
        private final BundleContext context;
        private final InjectableClass<?> clazz;

        public ServiceFactoryImpl(ServiceReference[] refs, ServiceCollection[] colls, BundleContext context, InjectableClass<?> clazz) {
            assert (this.refs.length == this.colls.length);
            this.refs = refs;
            this.colls = colls;
            this.context = context;
            this.clazz = clazz;
        }

        @Override
        public Object getService(Bundle bundle, ServiceRegistration sr) {
            if (verbose) {
                System.err.println("Instantiating " + clazz.getClassName());
            }
            Object[] objs = new Object[refs.length];
            for (int i = 0; i < refs.length; i++) {
                if (refs[i] != null) {
                    objs[i] = context.getService(refs[i]);
                    if (objs[i] == null) {
                        log("Failed to get " + (i + 1) + "th argument of " + clazz.getClassName());
                    }
                } else if (colls[i] != null) {
                    objs[i] = colls[i];
                } else {
                    log("Internal state error");
                    throw new RuntimeException();
                }
            }
            try {
                log("Calling constructor of " + clazz.getClassName());
                return clazz.newInstance(objs);
            } catch (RuntimeException x) {
                log("Failed to create object " + x.getMessage());
                throw x;
            }
        }

        @Override
        public void ungetService(Bundle bundle, ServiceRegistration sr, Object o) {
        }
    }
}
