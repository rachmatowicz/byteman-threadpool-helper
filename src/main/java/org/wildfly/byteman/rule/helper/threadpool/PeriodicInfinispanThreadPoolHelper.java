package org.wildfly.byteman.rule.helper.threadpool;

import org.apache.log4j.Logger;
import org.infinispan.executors.LazyInitializingBlockingTaskAwareExecutorService;
import org.infinispan.executors.LazyInitializingExecutorService;
import org.infinispan.executors.LazyInitializingScheduledExecutorService;
import org.jboss.byteman.rule.Rule;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class allows managing a set of references to ExecutorServices and printing out their threadpool statistics
 */
public class PeriodicInfinispanThreadPoolHelper extends InfinispanThreadPoolHelper
{
    private static Logger log = Logger.getLogger(PeriodicInfinispanThreadPoolHelper.class.getName());

    // each rule gets a separate helper instance, so these need to be static
    private static ConcurrentHashMap<String, Object> executorServiceMap = new ConcurrentHashMap<String,Object>();
    private static boolean periodicThreadStarted = false;

    public PeriodicInfinispanThreadPoolHelper(Rule rule)
    {
        super(rule);
    }

    /**
     * Add an executor service instance to the map of executor services monitored
     * @param managerName
     * @param executorName
     * @param executorService
     * @throws IllegalAccessException
     */
    public void addExecutorServiceToMap(String managerName, String executorName, Object executorService) throws IllegalAccessException
    {
        synchronized(executorServiceMap) {

            String entryName = managerName + ":" + executorName;
            // check for existing entry
            if (executorServiceMap.contains(entryName)) {
                log.warn("entry already exists for key: " + executorService);
                return;
            }

            // add new entry
            executorServiceMap.put(entryName, executorService);
            log.debug("added map entry for name:" + executorService + " on node " + getNodeName());

            if (!periodicThreadStarted) {
                log.debug("starting thread helper on node " + getNodeName());
                InfinispanPeriodicHelper.activated();
                periodicThreadStarted = true;
            }
        }
    }

    public void removeExecutorServiceFromMap(Object executorService) throws IllegalAccessException
    {
        boolean found = false;
        String executorName = null;

        synchronized(executorServiceMap) {

            // first find the executor name for the executor given
            for (Map.Entry<String, Object> entry : executorServiceMap.entrySet()) {
                String name = entry.getKey();
                Object value = entry.getValue();
                if (value.equals(executorService)) {
                    found = true ;
                    executorName = name;
                    break;
                }
            }

            if (!found) {
                log.warn("executor service not found in map: " + executorService + " on node " + getNodeName());
                return;
            }

            executorServiceMap.remove(executorName);
            log.debug("removed map entry:" + executorName + " on node " + getNodeName());

            if (executorServiceMap.size() == 0 && periodicThreadStarted) {
                log.debug("stopping thread helper on node " + getNodeName());
                InfinispanPeriodicHelper.deactivated();
                periodicThreadStarted = false;
            }
        }
    }

    /**
     * For each ServiceExecutor in the map, display its threadpool statistics
     * @return formatted line which displays the name and the threadpool stats of the supplied executor
     * @throws IllegalAccessException
     */
    public String dumpStatsForExecutorServiceInMap() throws IllegalAccessException
    {
        StringBuffer sb = new StringBuffer();
        sb.append("PERIODIC STATS FOR THREAD POOL EXECUTORS (" + getNodeName() + "):");
        sb.append("\n");

        if (executorServiceMap.size() == 0) {
            return "<< NO EXECUTORS INSTANTIATED>>" ;
        }

        // first sort the array of keys
        Set<String> keySet = executorServiceMap.keySet();
        String[] keyArray = keySet.toArray(new String[keySet.size()]);
        Arrays.sort(keyArray);

        // for each entry in the sorted keyset representing a serviceExecutor, add a record for its thread pool
        for (String name : keyArray) {
            Object executorService = executorServiceMap.get(name);

            if (executorService instanceof LazyInitializingExecutorService) {
                // write a record
                sb.append("  EXECUTOR: " + name + ", THREAD POOL: " + getThreadPoolStats((LazyInitializingExecutorService) executorService));
                sb.append("\n");
            } else if (executorService instanceof LazyInitializingScheduledExecutorService) {
                // write a record
                sb.append("  EXECUTOR: " + name + ", THREAD POOL: " + getThreadPoolStats((LazyInitializingScheduledExecutorService) executorService));
                sb.append("\n");
                //
            } else if (executorService instanceof LazyInitializingBlockingTaskAwareExecutorService) {
                // write a record
                sb.append("  EXECUTOR: " + name + ", THREAD POOL: " + getThreadPoolStats((LazyInitializingBlockingTaskAwareExecutorService) executorService));
                sb.append("\n");
                //
            } else {
                log.error("unrecognized executorService instance found in executorServiceMap!!!!");
            }
        }
        return sb.toString();
    }

    private String getNodeName() {
        return System.getProperty("jboss.node.name", "unknown");
    }
}
