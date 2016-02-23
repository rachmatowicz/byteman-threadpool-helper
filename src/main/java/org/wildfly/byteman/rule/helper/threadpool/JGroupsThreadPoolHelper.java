package org.wildfly.byteman.rule.helper.threadpool;

import org.jboss.byteman.rule.Rule;
import org.jboss.byteman.rule.helper.Helper;
import org.jgroups.protocols.TP;

import java.util.concurrent.ThreadPoolExecutor;

/**
 */
public class JGroupsThreadPoolHelper extends Helper
{
    /**
     * The transport contains three threadpools:
     * - regular (thread_ppol)
     * - OOB
     * - internal
     */
    public JGroupsThreadPoolHelper(Rule rule)
    {
        super(rule);
    }

    public String getRegularThreadPoolStats(TP transport)
    {
        // get the regular thread pool executor
        ThreadPoolExecutor tpe = (ThreadPoolExecutor) transport.getDefaultThreadPool();

        // return the thread pool stats
        return getThreadPoolStatistics(tpe);
    }

    public String getOOBThreadPoolStats(TP transport)
    {
        // get the regular thread pool executor
        ThreadPoolExecutor tpe = (ThreadPoolExecutor) transport.getOOBThreadPool();

        // return the thread pool stats
        return getThreadPoolStatistics(tpe);
    }

    public String getInternalThreadPoolStats(TP transport)
    {
        // get the regular thread pool executor
        ThreadPoolExecutor tpe = (ThreadPoolExecutor) transport.getInternalThreadPool();

        // return the thread pool stats
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

}
