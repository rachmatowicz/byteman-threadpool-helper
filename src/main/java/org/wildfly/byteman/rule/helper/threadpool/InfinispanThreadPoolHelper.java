package org.wildfly.byteman.rule.helper.threadpool;

import org.infinispan.executors.LazyInitializingBlockingTaskAwareExecutorService;
import org.infinispan.executors.LazyInitializingExecutorService;
import org.infinispan.executors.LazyInitializingScheduledExecutorService;
import org.infinispan.util.concurrent.BlockingTaskAwareExecutorService;
import org.infinispan.util.concurrent.BlockingTaskAwareExecutorServiceImpl;
import org.jboss.byteman.rule.Rule;
import org.jboss.byteman.rule.helper.Helper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Given an ExecutorService, determine the thread pool stats associated
 * <p/>
 * LazyInitializingExecutorService
 * - delegate field name = delegate:ExecutorService
 * LazyInitializingScheduledExecutorService
 * - delegate field name = delegate:ScheduledExecutorService
 * LazyInitializingBlockingTaskAwareExecutorService
 * - delegate field name = delegate:BlockingTaskAwareExecutorService
 * <p/>
 * Where should this go? Need a separate IntelliJ project to build the jar with byteman and ispn depes
 */
public class InfinispanThreadPoolHelper extends Helper
{
    public InfinispanThreadPoolHelper(Rule rule)
    {
        super(rule);
    }

    /**
     * This ExecutorService has a single delegate and obtaining the associated thread pool is easy.
     *
     * @param executorService the executor service whose ThreadPool we wish to inspect
     * @return the ThreadPoolExecutor for the executor service
     * @throws IllegalAccessException
     */
    public String getThreadPoolStats(LazyInitializingExecutorService executorService) throws IllegalAccessException
    {
        // get the thread pool executor
        ThreadPoolExecutor tpe = (ThreadPoolExecutor) getFieldValue(executorService, "delegate");

        // return the thread pool stats
        return getThreadPoolStatistics(tpe);
    }

    /**
     * This ExecutorService has two types of delegates and one of them has its thread pool hidden behind
     * a private inner class. Needs special treatment.
     *
     * @param executorService
     * @return
     * @throws IllegalAccessException
     */
    public String getThreadPoolStats(LazyInitializingScheduledExecutorService executorService) throws IllegalAccessException
    {
        // get the executor delegate
        ScheduledExecutorService executorServiceDelegate = (ScheduledExecutorService) getFieldValue(executorService, "delegate");
        // debug
        if (executorServiceDelegate != null) {
            System.out.println("ScheduledExecutorServiceDelegate class - " + executorServiceDelegate.getClass().getName());
        }

        // two cases:
        // - delegate is a ScheduledThreadPoolExecutor
        // - delegate is an Executors$DelegatedScheduledExecutorService

        if (executorServiceDelegate instanceof ScheduledThreadPoolExecutor) {
            // we have a ThreadPoolExecutor - just return the stats
            return getThreadPoolStatistics((ThreadPoolExecutor)executorServiceDelegate);
        } else {
            // we don't immediately have a ThreadPoolExecutor - drill down until we reach it
            // get the implementing class of the delegate
            Class executorsClazz = getExecutorsClass();
            // debug
            if (executorsClazz != null) {
                System.out.println("Executors class - " + executorsClazz.getName());
            }
            Class executorsInnerClazz = getDeclaredClass(executorsClazz, "java.util.concurrent.Executors$DelegatedScheduledExecutorService");
            // debug
            if (executorsInnerClazz != null) {
                System.out.println("Executors inner class - " + executorsInnerClazz.getName());
            }

            // now get the value of the delegate thread pool within the inner class
            ScheduledExecutorService ses = (ScheduledExecutorService) getFieldValue(executorsInnerClazz.cast(executorServiceDelegate), "e");

            // return the stats
            return getThreadPoolStatistics((ThreadPoolExecutor) ses);
        }
    }

    /**
     * This executor service has a single delegate and it is easy to get to the thread pool.
     *
     * @param executorService
     * @return
     */
    public String getThreadPoolStats(LazyInitializingBlockingTaskAwareExecutorService executorService) throws IllegalAccessException
    {
        // get the executor delegate
        BlockingTaskAwareExecutorService delegateExecutorService = (BlockingTaskAwareExecutorService) getFieldValue(executorService, "delegate");

        // need to get at the member variables in the implementation behind the interface:
        // the thread pool delegate
        // the blocking queue
        BlockingTaskAwareExecutorServiceImpl delegateExecutorServiceImpl = (BlockingTaskAwareExecutorServiceImpl) delegateExecutorService;
        ExecutorService delegateDelegateExecutorService = (ExecutorService) getFieldValue(delegateExecutorServiceImpl, "executorService");
        ThreadPoolExecutor tpe = (ThreadPoolExecutor) delegateDelegateExecutorService;

        // get the stats for the thread pool
        return getThreadPoolStatistics(tpe);
    }

    private String getThreadPoolStatistics(ThreadPoolExecutor tpe)
    {
        StringBuffer sb = new StringBuffer();
        sb.append("[pool, activePool, queuedTasks, completedTasks] = [");
        sb.append(tpe.getPoolSize() + ", ");
        sb.append(tpe.getActiveCount() + ", ");
        sb.append(tpe.getQueue().size() + ", ");
        sb.append(tpe.getCompletedTaskCount() + "]");
        return sb.toString();
    }

    /**
     * Get the class file for java.util.Executors
     * @return
     */
    private Class getExecutorsClass()
    {
        Class executorsClass = null;
        try {
            executorsClass = Class.forName("java.util.concurrent.Executors");
            return executorsClass;
        } catch (ClassNotFoundException cnfe) {
            System.out.println("Class not found: " + cnfe.getMessage());
        }
        return null;
    }

    /**
     * Return the value of the field of the given object
     * @param o         the object whose field value we want
     * @param fieldName the name of the field
     * @return the value of the field, or null
     * @throws IllegalAccessException
     */
    private Object getFieldValue(Object o, String fieldName) throws IllegalAccessException
    {
        Field f = null;
        try {
            f = o.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
        } catch (NoSuchFieldException nsfe) {
            System.out.println("Exception occurred: " + nsfe.getMessage());
        }
        return f.get(o);
    }

    /**
     * Return the result of invoking the method on the object with arguments
     * @param o    the object which is invoked on
     * @param m    the method to be invoked
     * @param args the arguments toi te method invocation
     * @return the return value from thye method invocation, or null
     * @throws IllegalAccessException
     */
    private Object getMethodReturnValue(Object o, Method m, Object... args) throws IllegalAccessException
    {
        Object retVal = null;
        try {
            retVal = m.invoke(o, args);
        } catch (Exception e) {
            System.out.println("Exception occurred: " + e.getMessage());
        }
        return retVal;
    }

    /**
     * Return the the declared class of the parent class with the given class name
     * @param parentClazz the parent class which defined the declared inner class
     * @param className   the name of the declared inner class
     * @return the clazz object, or null if the declared class nodes not exist
     */
    private Class<?> getDeclaredClass(Class parentClazz, String className)
    {
        Class[] classes = null;
        try {
            classes = parentClazz.getDeclaredClasses();
        } catch (SecurityException se) {
            System.out.println("Security exception: " + se.getMessage());
        }
        for (Class clazz : classes) {
            if (clazz.getName().equals(className)) {
                return clazz;
            }
        }
        return null;
    }

}
