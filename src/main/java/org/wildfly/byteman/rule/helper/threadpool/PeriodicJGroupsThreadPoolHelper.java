package org.wildfly.byteman.rule.helper.threadpool;

import org.apache.log4j.Logger;
import org.infinispan.executors.LazyInitializingBlockingTaskAwareExecutorService;
import org.infinispan.executors.LazyInitializingExecutorService;
import org.infinispan.executors.LazyInitializingScheduledExecutorService;
import org.jboss.byteman.rule.Rule;
import org.jgroups.protocols.TP;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * This class allows managing a set of references to ExecutorServices and printing out their threadpool statistics
 */
public class PeriodicJGroupsThreadPoolHelper extends JGroupsThreadPoolHelper
{
    private static Logger log = Logger.getLogger(PeriodicJGroupsThreadPoolHelper.class.getName());

    // each rule gets a separate helper instance, so these need to be static
    private static ConcurrentHashMap<String, Executor> executorMap = new ConcurrentHashMap<String,Executor>();
    private static boolean periodicThreadStarted = false;

    public PeriodicJGroupsThreadPoolHelper(Rule rule)
    {
        super(rule);
    }


    public void addTransportThreadPoolsToMap(TP transport) throws IllegalAccessException {
        addExecutorToMap("regular/default", transport.getDefaultThreadPool());
        addExecutorToMap("oob", transport.getOOBThreadPool());
        addExecutorToMap("internal", transport.getInternalThreadPool());

    }


    public void removeTransportThreadPoolsFromMap(TP transport) throws IllegalAccessException {
        removeExecutorFromMap(transport.getDefaultThreadPool());
        removeExecutorFromMap(transport.getOOBThreadPool());
        removeExecutorFromMap(transport.getInternalThreadPool());

    }

    /**
     * Add an executor service instance to the map of executor services monitored
     * @param executorName
     * @param executor
     * @throws IllegalAccessException
     */
    public void addExecutorToMap(String executorName, Executor executor) throws IllegalAccessException
    {
        if (executor == null)
            throw new IllegalArgumentException("Executor instance input cannot be null");

        synchronized(executorMap) {

            String entryName = executorName;
            // check for existing entry
            if (executorMap.contains(entryName)) {
                log.warn("entry already exists for key: " + executor);
                return;
            }

            // add new entry
            executorMap.put(entryName, executor);
            log.debug("added map entry for name:" + executor + " on node " + getNodeName());

            if (!periodicThreadStarted) {
                log.debug("starting thread helper on node " + getNodeName());
                JGroupsPeriodicHelper.activated();
                periodicThreadStarted = true;
            }
        }
    }

    public void removeExecutorFromMap(Object executor) throws IllegalAccessException
    {
        boolean found = false;
        String executorName = null;

        if (executor == null)
            throw new IllegalArgumentException("Executor instance input cannot be null");

        synchronized(executorMap) {

            // first find the executor name for the executor given
            for (Map.Entry<String, Executor> entry : executorMap.entrySet()) {
                String name = entry.getKey();
                Executor value = entry.getValue();
                if (value.equals(executor)) {
                    found = true ;
                    executorName = name;
                    break;
                }
            }

            if (!found) {
                log.warn("executor service not found in map: " + executor + " on node " + getNodeName());
                return;
            }

            executorMap.remove(executorName);
            log.debug("removed map entry:" + executorName + " on node " + getNodeName());

            if (executorMap.size() == 0 && periodicThreadStarted) {
                log.debug("stopping thread helper on node " + getNodeName());
                JGroupsPeriodicHelper.deactivated();
                periodicThreadStarted = false;
            }
        }
    }

    /**
     * For each ServiceExecutor in the map, display its threadpool statistics
     * @return formatted line which displays the name and the threadpool stats of the supplied executor
     * @throws IllegalAccessException
     */
    public String dumpStatsForExecutorInMap() throws IllegalAccessException
    {
        StringBuffer sb = new StringBuffer();
        sb.append("PERIODIC STATS FOR JGROUPS THREAD POOL EXECUTORS (" + getNodeName() + "):");
        sb.append("\n");

        if (executorMap.size() == 0) {
            return "<< NO EXECUTORS INSTANTIATED>>" ;
        }

        // first sort the array of keys
        Set<String> keySet = executorMap.keySet();
        String[] keyArray = keySet.toArray(new String[keySet.size()]);
        Arrays.sort(keyArray);

        // for each entry in the sorted keyset representing a serviceExecutor, add a record for its thread pool
        for (String name : keyArray) {
            Executor executor = executorMap.get(name);

            if (executor instanceof ThreadPoolExecutor) {
                // write a record
                sb.append("  EXECUTOR: " + name + ", THREAD POOL: " + getThreadPoolStatistics((ThreadPoolExecutor) executor));
                sb.append("\n");
            } else {
                log.error("unrecognized executor instance found in executors map!!!!");
            }
        }
        return sb.toString();
    }

    private String getNodeName() {
        return System.getProperty("jboss.node.name", "unknown");
    }
}
