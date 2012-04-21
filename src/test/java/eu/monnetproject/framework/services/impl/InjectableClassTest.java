package eu.monnetproject.framework.services.impl;

import eu.monnetproject.framework.services.Inject;
import java.lang.reflect.Type;
import java.util.Arrays;
import junit.framework.TestCase;

/**
 *
 * @author jmccrae
 */
public class InjectableClassTest extends TestCase {
    
    public InjectableClassTest(String testName) {
        super(testName);
    }
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }
    
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public static class IJ1 {
        @Inject public IJ1(String s) {
            
        }
    }
    
    public static class IJ2 {
        
    }
    
    public static class IJ3 {
        public IJ3(String s) {
            System.out.println(s);
        }
    }
    
    
    /**
     * Test of dependencies method, of class InjectableClass.
     */
    public void testDependencies1() {
        System.out.println("dependencies");
        InjectableClass instance = new InjectableClass(IJ1.class);
        Type[] expResult = { String.class };
        Type[] result = instance.dependencies();
        assertTrue(Arrays.equals(expResult, result));
    }
    /**
     * Test of dependencies method, of class InjectableClass.
     */
    public void testDependencies2() {
        System.out.println("dependencies");
        InjectableClass instance = new InjectableClass(IJ2.class);
        Type[] expResult = { };
        Type[] result = instance.dependencies();
        assertTrue(Arrays.equals(expResult, result));
    }
    /**
     * Test of dependencies method, of class InjectableClass.
     */
    public void testDependencies3() {
        System.out.println("dependencies");
        InjectableClass instance = new InjectableClass(IJ3.class);
        Type[] expResult = { String.class };
        Type[] result = instance.dependencies();
        assertTrue(Arrays.equals(expResult, result));
    }

    /**
     * Test of newInstance method, of class InjectableClass.
     */
    public void testNewInstance1() {
        System.out.println("newInstance");
        Object[] args = { "test" };
        InjectableClass instance = new InjectableClass(IJ1.class);
        Object result = instance.newInstance(args);
        assertNotNull(result);
    }
}
