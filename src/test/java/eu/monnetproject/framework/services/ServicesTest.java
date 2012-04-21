package eu.monnetproject.framework.services;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.service.log.LogService;

/**
 *
 * @author jmccrae
 */
public class ServicesTest {

    public static final String ARTIFACT = "target/framework.services-1.12.3-SNAPSHOT.jar";

    public ServicesTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testNonOSGi() throws Exception {
        final URLClassLoader classLoader = new URLClassLoader(new URL[]{
                    new File("src/test/resources/services-1.0-SNAPSHOT.jar").toURI().toURL(),
                    new File("src/test/resources/dep_impl-1.0-SNAPSHOT.jar").toURI().toURL(),
                    new File("src/test/resources/indep_impl-1.0-SNAPSHOT.jar").toURI().toURL(),
                }, Thread.currentThread().getContextClassLoader());
        Thread.currentThread().setContextClassLoader(classLoader);
        classLoader.loadClass("eu.monnetproject.framework.services.dep_impl.DepByGet");
        Services.get(classLoader.loadClass("eu.monnetproject.framework.services.Service2"));
        Services.get(classLoader.loadClass("eu.monnetproject.framework.services.Service1"));
    }

    @Test
    public void testOSGi() throws Exception {
        System.err.println("testOSGi");
        deleteRecursive(new File("felix-cache"));
        final Map<String, String> props = new HashMap<String, String>();
        props.put(Constants.FRAMEWORK_SYSTEMPACKAGES_EXTRA, "org.osgi.service.log;version=1.3.0");
        final Framework fw = getFrameworkFactory().newFramework(props);
        fw.init();
        fw.start();
        final BundleContext fwContext = fw.getBundleContext();
        final Bundle servicesBundle = startBundle(fwContext, "services");
        final Bundle consumerBundle = startBundle(fwContext, "consumerOSGi");
        final Bundle depImplBundle = startBundle(fwContext, "dep_impl");
        final Bundle indepImplBundle = startBundle(fwContext, "indep_impl");
        final File generatedFile = new File(ARTIFACT);
        if (!generatedFile.exists()) {
            System.err.println("Skipping this test as the target jar is not available");
            return;
        }
        final Bundle fsBundle = fwContext.installBundle(generatedFile.toURI().toURL().toString());
        servicesBundle.start();
        consumerBundle.start();
        depImplBundle.start();
        indepImplBundle.start();
        fsBundle.start();
        assertNotNull(indepImplBundle.getBundleContext().getServiceReference("eu.monnetproject.framework.services.Service2"));
        final ServiceReference[] srs = depImplBundle.getBundleContext().getServiceReferences("eu.monnetproject.framework.services.Service1", null);
        assertEquals(1, srs.length);
        synchronized (this) {
            try {
                wait(1000);
            } catch (InterruptedException x) {
            }
        }
        fw.stop();
        deleteRecursive(new File("felix-cache"));
        // Expected output:
//Starting consumer
//Dependent hello consumer from Independent
//Multiple Dependent hello consumer from Independent
//Found a service Independent
//Hello, I am busy waiting for other services
//Lost a service Independent
    }

    @Test
    public void testOSGiProvide() throws Exception {
        System.err.println("testOSGiProvide");
        deleteRecursive(new File("felix-cache2"));
        final Map<String, String> props = new HashMap<String, String>();
        props.put(Constants.FRAMEWORK_SYSTEMPACKAGES_EXTRA, "org.osgi.service.log;version=1.3.0");
        props.put(Constants.FRAMEWORK_STORAGE,"felix-cache2");
        final Framework fw = getFrameworkFactory().newFramework(props);
        fw.init();
        fw.start();
        final BundleContext fwContext = fw.getBundleContext();
        final Bundle servicesBundle = startBundle(fwContext, "services");
        final Bundle consumerBundle = startBundle(fwContext, "consumerOSGi");
        final Bundle depImplBundle = startBundle(fwContext, "dep_impl");
        final Bundle indepImplBundle = startBundle(fwContext, "depOSGi");
        final File generatedFile = new File(ARTIFACT);
        if (!generatedFile.exists()) {
            System.err.println("Skipping this test as the target jar is not available");
            return;
        }
        final Bundle fsBundle = fwContext.installBundle(generatedFile.toURI().toURL().toString());
        servicesBundle.start();
        consumerBundle.start();
        depImplBundle.start();
        indepImplBundle.start();
        fsBundle.start();
        assertNotNull(indepImplBundle.getBundleContext().getServiceReference("eu.monnetproject.framework.services.Service2"));
        final ServiceReference[] srs = depImplBundle.getBundleContext().getServiceReferences("eu.monnetproject.framework.services.Service1", null);
        assertEquals(1, srs.length);
        synchronized (this) {
            try {
                wait(1000);
            } catch (InterruptedException x) {
            }
        }
        fw.stop();
        deleteRecursive(new File("felix-cache2"));
        // Expected output:
//Starting consumer
//Multiple Dependent hello consumer from OSGi
//Found a service OSGi
//Hello, I am busy waiting for other services
//Lost a service OSGi
    }

    private void deleteRecursive(File directory) {
        if (!directory.exists()) {
            return;
        }
        if (directory.isDirectory()) {
            for (File file : directory.listFiles()) {
                deleteRecursive(file);
            }
        }
        directory.delete();
    }

    private Bundle startBundle(final BundleContext fwContext, final String bundleName) throws BundleException, MalformedURLException {
        return fwContext.installBundle(new File("src/test/resources/" + bundleName + "-1.0-SNAPSHOT.jar").toURI().toURL().toString());
    }

    private static FrameworkFactory getFrameworkFactory() throws Exception {
        URL url = ServicesTest.class.getClassLoader().getResource(
                "META-INF/services/org.osgi.framework.launch.FrameworkFactory");
        if (url != null) {
            BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
            try {
                for (String s = br.readLine(); s != null; s = br.readLine()) {
                    s = s.trim();
                    // Try to load first non-empty, non-commented line.
                    if ((s.length() > 0) && (s.charAt(0) != '#')) {
                        return (FrameworkFactory) Class.forName(s).newInstance();
                    }
                }
            } finally {
                if (br != null) {
                    br.close();
                }
            }
        }

        throw new Exception("Could not find framework factory.");
    }
    
    @Test
    public void testAdvanced() throws Exception {
        System.setProperty("eu.monnetproject.framework.services.verbose", "true");
        // Test the advanced features
        System.err.println("testAdvanced");
        deleteRecursive(new File("felix-cache3"));
        final Map<String, String> props = new HashMap<String, String>();
        props.put(Constants.FRAMEWORK_SYSTEMPACKAGES_EXTRA, "org.osgi.service.log;version=1.3.0");
        props.put(Constants.FRAMEWORK_STORAGE,"felix-cache3");
        final Framework fw = getFrameworkFactory().newFramework(props);
        fw.init();
        fw.start();
        final BundleContext fwContext = fw.getBundleContext();
        final Bundle advancedBundle = startBundle(fwContext, "advanced");
        
        final File generatedFile = new File(ARTIFACT);
        if (!generatedFile.exists()) {
            System.err.println("Skipping this test as the target jar is not available");
            return;
        }
        final Bundle thisBundle = fwContext.installBundle(generatedFile.toURI().toURL().toString());
        advancedBundle.start();
        thisBundle.start();
        
        assertNotNull(advancedBundle.getBundleContext().getServiceReference("eu.monnetproject.framework.services.advanced.DepService"));
        assertNotNull(advancedBundle.getBundleContext().getServiceReference("eu.monnetproject.framework.services.advanced.DepService2"));
        assertNull(advancedBundle.getBundleContext().getServiceReference("eu.monnetproject.framework.services.advanced.DepServiceUnsatisfiable"));
        synchronized (this) {
            try {
                wait(1000);
            } catch (InterruptedException x) {
            }
        }
        fw.stop();
        deleteRecursive(new File("felix-cache3"));
    }
}
