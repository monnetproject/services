/**********************************************************************************
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
 *********************************************************************************/
package eu.monnetproject.framework.services;

import eu.monnetproject.framework.services.impl.StdResolver;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.LinkedList;

/**
 * Interface for resolving services in a non-OSGi environment.
 * 
 * @author John McCrae
 */
public final class Services {

    private Services() { }
    
    /**
     * Get a single instance of a service
     * @param serviceClass The class that the service must implement
     * @throws ServiceLoadException If either the service could not be loaded due to
     * missing dependencies, a declaration file for this service class referenced an
     * invalid class name, the service implementation does not implement the service 
     * interface or the service threw an exception when instantiated.
     * @return The service.
     */
    public static <S> S get(Class<S> serviceClass) {
        return StdResolver.resolveImmediate(serviceClass);
    }
    
    /**
     * Get a (non-static) collection of all available services. Note the contents
     * of the collection may change if the state of the system changes
     * @param serviceClass The class that the service must implement
     * @throws ServiceLoadException If either the service could not be loaded due to
     * missing dependencies, a declaration file for this service class referenced an
     * invalid class name or the service implementation does not implement the service 
     * interface.
     * @return A (possibly empty) collection of services implementing the service. Note
     * services are not instantiated until the {@code next();} function is called.
     */
    public static <S> ServiceCollection<S> getAll(Class<S> serviceClass) {
        return StdResolver.resolveExtant(serviceClass);
    }
    
    /**
     * Get a factor over the services. This factory works similar to getAll but
     * calls all services in order
     * @param serviceClass The class of the factory
     * @return A proxy object that returns the first matching call for each class
     */
    public static <S> S getFactory(final Class<S> serviceClass) {
        @SuppressWarnings("unchecked")
        final S service = (S) Proxy.newProxyInstance(serviceClass.getClassLoader(),
                           new Class<?>[]{serviceClass}, new InvocationHandler() {
                               private LinkedList<S> services;
                               
                               
                       @Override
                       public Object invoke(Object o, Method method, Object[] os) throws Throwable {
                           if(services == null) {
                               services = new LinkedList<S>();
                               for (S s : getAll(serviceClass)) {
                                   services.add(s);
                               }
                           }
                           if (method.getDeclaringClass().equals(Object.class)) {
                               return method.invoke(serviceClass, os);
                           } else {
                               if (method.getReturnType().equals(Collection.class)) {
                                   LinkedList rval = new LinkedList();
                                   for(S s : services) {
                                       Object res = null;
                                       try {
                                           res = method.invoke(s, os);
                                       } catch (Throwable t) {
                                       }
                                       if (res != null) {
                                           rval.addAll((Collection) res);
                                       }
                                   }
                                   return rval;

                               } else {
                                   for(S s : services) {
                                       Object rval = null;
                                       try {
                                           rval = method.invoke(s, os);
                                       } catch (Throwable t) {
                                       }
                                       if (rval != null) {
                                           return rval;
                                       }
                                   }
                                   return null;
                               }
                           }
                       }
                   });
        
        return service;
    }
}
