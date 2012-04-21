Monnet Framework Services (MFS)
===============================

*An easy way to do OSGi services*

What is MFS?
------------

MFS is a simple system for providing a service-orientated architecture with an
"inversion of control" methodology. MFS builds on the OSGi framework, but provides
a simple way to use this framework and will function also without an OSGi runtime.
Further, MFS allows a service framework to be created without the need for special
coding patterns or clunky libraries. MFS was developed under the 
[Monnet Project](http://www.monnet-project.eu/) to meet the challenge of aggregating
multiple Natural Language Processsing toolkits for many languages under a 
single architecture.

Introduction to MFS
-------------------

A service in MFS is simply a (POJO) class with a single constructor and implements some
interface. For example:

    package com.mycompany;
 
    public class TokenizerImpl implements Tokenizer {
       public TokenizerImpl() { }
    }

Services are declared by including a file in the JAR under the path 
`META-INF/components` whose name is exactly the interface and whose contains are all
implementations in this JAR (bundle). For example for the above example we would
have a file called `META-INF/components/com.mycompany.Tokenizer` and may be as follows

    com.mycompany.TokenizerImpl
    com.mycompany.AnotherTokenizerImpl

The dependencies of a service are given by the arguments of this single constructor.
For example we may define a part-of-speech tagger that is dependent on a Tokenizer
as follows:

     package com.mycompany;

     public class POSTaggerImpl implements POSTagger {
         public POSTaggerImpl(Tokenizer tokenizer) { }
     }

This `POSTaggerImpl` service will be available in the OSGi framework only when an
implementation of the `Tokenizer` interface is also available.

Multiple dependencies may be indicated by using the `java.util.Collection` class 
or any of its parents, e.g.,

     package com.mycompany;

     public class POSTaggerImpl implements POSTagger {
         public POSTaggerImpl(Collection<Tokenizer> tokenizers) { }
     }

The type of service is given by the generic parameter. Note that the contents of 
this collection object may change as services become (un)available.

Services can finally be obtained through the OSGi service registry as usual. This
should generally be done at the application level (for example in a Servlet or Bundle
Activator), e.g.,


    public class MyActivator extends BundleActivator {
       @Override
       public void start(BundleContext bundleContext) throws Exception {
          final ServiceReference serviceRef = bundleContext.getServiceReference(
                    POSTagger.class.getName());
          if(serviceRef != null) {
               final POSTagger posTagger = (POSTagger)bundleContext.getService(serviceRef);
               // Do something with the posTagger
          }
       }
       // etc
    }

Using MFS without OSGi
----------------------

For testing and agile development purposes MFS can also be used without an OSGi
runtime. This is done through the class `eu.monnetproject.framework.services.Services`.
A single instance of a service can thus be obtained as follows:

    final POSTagger posTagger = Services.get(POSTagger.class);

The dependencies are resolved recursively so the above statement with the classes 
defined as above are equivalent to 

    final POSTagger posTagger = new POSTaggerImpl(Services.get(Tokenizer.class));

And then

    final POSTagger posTagger = new POSTaggerImpl(new TokenizerImpl());

However as MFS does not require that the name of the implementations be specified 
this allows for implementations to be changed without affecting dependent code.

All instances of a service may be obtained through the method getAll

    final Collection<POSTagger> allTaggers = Services.getAll(POSTagger.class);

Advanced Features
-----------------

MFS in addition provides a small number of annotations for customizing the way 
that MFS resolves services.

### @Inject

The @Inject annotation can be used to indicate which constructor is to be used for
dependency injection. This can be useful for integrating MFS with other frameworks

    public class MyPOSTagger implements POSTagger {
       // Do not use for injection
       public MyPOSTagger() {}

       // Use for injection
       @Inject public MyPOSTagger(Tokenizer tokenizer) { }
    }

This annotation must be used if there are more than one constructor

### @NonEmpty

This annotation when applied to a constructor argument of a `Collection` will 
ensure the service is only created when there is at least one satisfying dependency

    public class MyPOSTagger implements POSTagger {
       public MyPOSTagger(@NonEmpty Collection<Tokenizer> tokenizers) { }
    }

### @Singleton

This annotation means that MFS will only return one instance of the class

    @Singleton public class MyPOSTagger implements POSTagger {
       public MyPOSTagger(Collection<Tokenizer> tokenizers) { }
    }

Note MFS does not guarantee that the class will only be constructed once, so this
should not be used to perform tasks that can only be once (e.g., connecting to a 
database). This annotation is intended to save on resources by avoiding duplicate
instances of similar classes. Similarly, if dependent services become unavailable 
and then new dependencies become available the constructor will be called again.
