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
package eu.monnetproject.framework.services;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

/**
 * Thrown is a service could not be loaded, normally as a service definition file
 * was misformatted or a class is missing
 *
 * @author John McCrae
 */
public class ServiceLoadException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private String msg = null;
    private final List<Class<?>> notFounds;
    
    /**
     * Creates a new instance of <code>ServiceLoadException</code> without detail message.
     */
    public ServiceLoadException(Class<?> notFound) {
        notFounds = new LinkedList<Class<?>>();
        notFounds.add(notFound);
    }


    public ServiceLoadException(Class<?> notFound, Throwable thrwbl) {
        super(thrwbl);
        notFounds = new LinkedList<Class<?>>();
        notFounds.add(notFound);
    }
    
    
    public ServiceLoadException(Class<?> notFound, String msg) {
        super(msg);
        this.msg = msg;
        notFounds = new LinkedList<Class<?>>();
        notFounds.add(notFound);
    }
    
    public ServiceLoadException(ServiceLoadException x, Class<?> notFound) {
        notFounds = new LinkedList<Class<?>>(x.getNotFounds());
        notFounds.add(notFound);
    }
 
    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("Could not load service ").append(notFounds.get(0).getSimpleName());
        final ListIterator<Class<?>> listIterator = notFounds.listIterator(1);
        while(listIterator.hasNext()) {
            sb.append(" required by ").append(listIterator.next().getSimpleName());
        }
        if(msg != null) {
            sb.append(" (").append(msg).append(")");
        }
        return sb.toString();
    }

    public List<Class<?>> getNotFounds() {
        return notFounds;
    }
    
    
}
