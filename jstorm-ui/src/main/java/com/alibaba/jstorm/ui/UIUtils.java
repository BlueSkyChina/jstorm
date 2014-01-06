package com.alibaba.jstorm.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import javax.faces.context.FacesContext;

import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransportException;
import org.yaml.snakeyaml.Yaml;

import backtype.storm.Config;
import backtype.storm.generated.Bolt;
import backtype.storm.generated.ClusterSummary;
import backtype.storm.generated.ErrorInfo;
import backtype.storm.generated.Nimbus.Client;
import backtype.storm.generated.SpoutSpec;
import backtype.storm.generated.StormTopology;
import backtype.storm.generated.SupervisorSummary;
import backtype.storm.generated.TaskStats;
import backtype.storm.generated.TaskSummary;
import backtype.storm.generated.TopologyInfo;
import backtype.storm.generated.TopologySummary;
import backtype.storm.scheduler.WorkerSlot;
import backtype.storm.utils.Utils;

import com.alibaba.jstorm.common.stats.StatBuckets;
import com.alibaba.jstorm.common.stats.StaticsType;
import com.alibaba.jstorm.ui.model.ClusterSumm;
import com.alibaba.jstorm.ui.model.Components;
import com.alibaba.jstorm.ui.model.SupervisorSumm;
import com.alibaba.jstorm.ui.model.TopologySumm;
import com.alibaba.jstorm.utils.JStormUtils;

public class UIUtils {

    
    private static int maxErrortime = 1800;
    private static int maxErrornum = 200;
    
    public static String getWindow(FacesContext ctx) {
        String window = null;
        if (ctx.getExternalContext().getRequestParameterMap().get("window") != null) {
            window = (String) ctx.getExternalContext().getRequestParameterMap()
                    .get("window");
        }
        
        return StatBuckets.getTimeKey(window);
    }


    public static List nimbusClientandConn(String host, Integer port)
            throws TTransportException {
        TSocket ts = new TSocket(host, port);
        TFramedTransport tt = new TFramedTransport(ts);
        TBinaryProtocol prot = new TBinaryProtocol(tt);
        Client nc = new Client(prot);
        ts.open();
        List l = new ArrayList();
        l.add(nc);
        l.add(tt);
        return l;
    }

    public static final String SPOUT_STR = "spout";
    public static final String BOLT_STR  = "bolt";
    
    public static String componentType(StormTopology topology, String id) {
        Map<String, Bolt> bolts = topology.get_bolts();
        Map<String, SpoutSpec> spouts = topology.get_spouts();
        String type = "";
        if (bolts.containsKey(id)) {
            type = BOLT_STR;
        } else if (spouts.containsKey(id)) {
            type = SPOUT_STR;
        }
        return type;
    }
    

    private List<Double> addPairs(ArrayList<Double> p1, ArrayList<Double> p2) {
        if (p1 == null || p2 == null) {
            return null;
        } else {
            int p1Szie = p1.size();
            int p2Size = p2.size();
            if (p1Szie != p1Szie) {
                return null;
            } else {
                List<Double> rtn = new ArrayList<Double>();
                for (int i = 0; i < p1Szie; i++) {
                    rtn.set(i, p1.get(i) + p2.get(i));
                }
                return rtn;
            }
        }

    }
    
    

    public static void addTask(Map<String, List<TaskSummary>> taskMap, TaskSummary t,
            String componentId) {
        
        List<TaskSummary> taskList = taskMap.get(componentId);
        
        if (taskList == null) {
            taskList = new ArrayList<TaskSummary>();
            taskMap.put(componentId, taskList);
        }
        
        taskList.add(t);
    }
    
    /**
     * Merge stream value
     * 
     * @param rawMap  Map<String, Map<K, V>> rawMap
     * @param keySample
     * @param zeroValue    0 Value
     * @return  Map<String, V>
     */
    public static    <K, V> Map<String, V>  mergeStream(Map<String, Map<K, V>> rawMap, 
            V zeroValue) {
        Map<String, V> ret = new HashMap<String, V>();
        
        for (Entry<String, Map<K, V>> rawEntry : rawMap.entrySet()) {
            String window = rawEntry.getKey();
            Map<K, V> streamMap = rawEntry.getValue();
            
            V retValue = zeroValue;
            
            if (streamMap != null) {
                for (Entry<K, V> streamEntry : streamMap.entrySet()) {
                    K entry = streamEntry.getKey();
                    V counter = streamEntry.getValue();
                    
                    retValue = (V)JStormUtils.add(retValue, counter);
                }
            }
            
            ret.put(window, retValue);
        }
        
        return ret;
    }
    
    public static Map<StaticsType, List<Object>> mergeStream(List<TaskSummary> taskSummaries, String window) {
        
        
        
        Map<StaticsType, List<Object>> ret = new HashMap<StaticsType, List<Object>>();
        
        List<Object>   emitted = new ArrayList<Object>();
        List<Object>   sendTps = new ArrayList<Object>(); 
        List<Object>   recvTps = new ArrayList<Object>();
        List<Object>   acked = new ArrayList<Object>();
        List<Object>   failed = new ArrayList<Object>();
        List<Object>   process = new ArrayList<Object>();
        
        ret.put(StaticsType.emitted, emitted);
        ret.put(StaticsType.send_tps, sendTps);
        ret.put(StaticsType.recv_tps, recvTps);
        ret.put(StaticsType.acked, acked);
        ret.put(StaticsType.failed, failed);
        ret.put(StaticsType.process_latencies, process);
        
        
        for (TaskSummary taskSummary : taskSummaries) {
            TaskStats taskStats = taskSummary.get_stats();
            
            if (taskStats == null) {
                continue;
            }
            
            Map<String, Long> emittedMap = mergeStream(taskStats.get_emitted(), Long.valueOf(0));
            emitted.add(emittedMap.get(window));
            
            Map<String, Double> rendTpsMap = mergeStream(taskStats.get_send_tps(), Double.valueOf(0));
            sendTps.add(rendTpsMap.get(window));
            
            Map<String, Double> recvTpsMap = mergeStream(taskStats.get_recv_tps(), Double.valueOf(0));
            recvTps.add(recvTpsMap.get(window));
            
            Map<String, Long> ackedMap = mergeStream(taskStats.get_acked(), Long.valueOf(0));
            acked.add(ackedMap.get(window));
            
            Map<String, Long> failedMap = mergeStream(taskStats.get_failed(), Long.valueOf(0));
            failed.add(failedMap.get(window));
            
            Map<String, Double> processMap = mergeStream(taskStats.get_process_ms_avg(), Double.valueOf(0));
            process.add(processMap.get(window));
        }
        
        return ret;
        
    }
    
    public static Map<StaticsType, Object> mergeTasks(List<TaskSummary> taskSummaries, String window) {
        Map<StaticsType, Object> ret = new HashMap<StaticsType, Object>();
        
        Map<StaticsType, List<Object>> mergedStreamTasks = mergeStream(taskSummaries, window);
        for (Entry<StaticsType, List<Object>> entry : mergedStreamTasks.entrySet()) {
            StaticsType type = entry.getKey();
            List<Object> valueList = entry.getValue();
            
            Object  valueSum = JStormUtils.mergeList(valueList);
            
            ret.put(type, valueSum);
        }
        
        return ret;
    }

    
    public static Components getComponent(List<TaskSummary> taskSummaries,
            String componentId, String type, String window) {
        
        Map<StaticsType, Object> staticsType = UIUtils.mergeTasks(
                taskSummaries, window);
        
        Components component = new Components();
        component.setType(type);
        component.setComponetId(componentId);
        component.setParallelism(String.valueOf(taskSummaries.size()));
        component.setValues(staticsType);
        
        return component;
    }
    
    public static List<TaskSummary> getTaskList(List<TaskSummary> taskSummaries,
            String componentId) {
        List<TaskSummary> ret = new ArrayList<TaskSummary>();
        
        for (TaskSummary task : taskSummaries) {
            if (componentId.equals(task.get_component_id())) {
                ret.add(task);
            }
        }
        
        return ret;
    }
    

    public static String mostRecentError(List<TaskSummary> summarys) {
        TreeMap<Integer, String> map = new TreeMap<Integer, String>(new DescendComparator());
        int summarysSzie = 0;
        if (summarys != null) {
            summarysSzie = summarys.size();
        }
        for (int i = 0; i < summarysSzie; i++) {
            List<ErrorInfo> einfos = summarys.get(i).get_errors();
            if (einfos != null) {
                int einfoSize = einfos.size();
                for (int j = 0; j < einfoSize; j++) {
                    ErrorInfo einfo = einfos.get(j);
                    long current = System.currentTimeMillis() / 1000;
                    if (current - einfo.get_error_time_secs() < maxErrortime) {
                        map.put(new Integer(einfo.get_error_time_secs()), einfo.get_error());
                    }
                }
            }

        }
        String rtn = "";
        Collection<String> values = map.values();
        int size = 0;
        for (String s : values) {
            if (size >= maxErrornum) {
                break;
            }
            rtn += s + ";";
            size++;
        }
        return rtn;
    }
    
    public static String getTaskError(List<ErrorInfo> errList) {
        if (errList == null) {
            return "";
        }
        
        TreeMap<Integer, String> map = new TreeMap<Integer, String>(new DescendComparator());

        for (ErrorInfo einfo : errList) {

            long current = System.currentTimeMillis() / 1000;
            if (current - einfo.get_error_time_secs() < maxErrortime) {
                map.put(new Integer(einfo.get_error_time_secs()), einfo.get_error());
            }
        }
        
        
        String rtn = "";
        Collection<String> values = map.values();
        int size = 0;
        for (String s : values) {
            if (size >= maxErrornum) {
                break;
            }
            rtn += s + ";";
            size++;
        }
        return rtn;
    }

    
    
    /**
     * Convert thrift TopologySummary to UI bean TopologySumm
     * @param ts
     * @return
     */
    public static List<TopologySumm> topologySummary(
            List<TopologySummary> ts) {
        
        List<TopologySumm> tsumm = new ArrayList<TopologySumm>();
        if (ts != null) {
            for (TopologySummary t : ts) {
                
                
                TopologySumm topologySumm = new TopologySumm();
                topologySumm.setTopologyId(t.get_id());
                topologySumm.setTopologyName(t.get_name());
                topologySumm.setStatus(t.get_status());
                topologySumm.setUptime(StatBuckets.prettyUptimeStr(t
                                .get_uptime_secs()));
                
                topologySumm.setNumCpu(String.valueOf(t.get_num_cpu()));
                topologySumm.setNumMem(String.valueOf(t.get_num_mem()));
                topologySumm.setNumDisk(String.valueOf(t.get_num_disk()));
                topologySumm.setNumWorkers(String.valueOf(t.get_num_workers()));
                topologySumm.setNumTasks(String.valueOf(t.get_num_tasks()));
                
                
                
                tsumm.add(topologySumm);
            }
        }
        return tsumm;
    }
    
    /**
     * Convert thrift TopologyInfo to UI bean TopologySumm
     * @param summ
     * @return
     */
    public static List<TopologySumm> topologySummary(TopologyInfo t) {
        
        List<TaskSummary> tasks = t.get_tasks();
        Set<WorkerSlot> workers = new HashSet<WorkerSlot>();
        int taskNum = 0;
        
        int cpuNum = 0;
        int memNum = 0;
        int diskNum = 0;
        Set<Integer> ports = new HashSet<Integer>();
        if (tasks != null) {
            taskNum = tasks.size();
            for (int i = 0; i < taskNum; i++) {
                TaskSummary task = tasks.get(i);
                
                cpuNum += task.get_cpu();
                memNum += task.get_mem();
                
                if (task.get_disk() != null && task.get_disk().isEmpty() == false) {
                    diskNum++;
                }
                ports.add(task.get_port());
                workers.add(new WorkerSlot(task.get_host(), task.get_port()));
                
            }
        }
        
        List<TopologySumm> tsumm = new ArrayList<TopologySumm>();
        
//        TopologySumm ts = new TopologySumm(summ.get_name(), summ.get_id(),
//                summ.get_status(), StatBuckets.prettyUptimeStr(summ
//                        .get_uptime_secs()), String.valueOf(workers.size()),
//                String.valueOf(taskSize), summ.get_uptime_secs());
        
        
        TopologySumm topologySumm = new TopologySumm();
        topologySumm.setTopologyId(t.get_id());
        topologySumm.setTopologyName(t.get_name());
        topologySumm.setStatus(t.get_status());
        topologySumm.setUptime(StatBuckets.prettyUptimeStr(t
                        .get_uptime_secs()));
        
        topologySumm.setNumCpu(String.valueOf(cpuNum));
        topologySumm.setNumMem(String.valueOf(memNum));
        topologySumm.setNumDisk(String.valueOf(diskNum));
        topologySumm.setNumWorkers(String.valueOf(workers.size()));
        topologySumm.setNumTasks(String.valueOf(taskNum));
        
        tsumm.add(topologySumm);
        return tsumm;
    }
    
    /**
     * Connvert thrift ClusterSummary to UI bean ClusterSumm
     * @param summ
     * @return
     */
    public static List<ClusterSumm> clusterSummary(ClusterSummary summ, 
            String masterHostname) {
        // "Supervisors" "Used slots" "Free slots" "Total slots" "Running task"
        List<SupervisorSummary> sups = summ.get_supervisors();
        int supSize = 0;
        
        int totalCpuSlots = 0;
        int useCpuSlots = 0;
        int freeCpuSlots = 0;
        
        int totalMemSlots = 0;
        int useMemSlots = 0;
        int freeMemSlots = 0;
        
        int totalDiskSlots = 0;
        int useDiskSlots = 0;
        int freeDiskSlots = 0;
        
        int totalPortSlots = 0;
        int usePortSlots = 0;
        int freePortSlots = 0;
        
        
        
        
        if (sups != null) {
            supSize = sups.size();
            for (SupervisorSummary ss : sups) {
                totalCpuSlots += ss.get_num_cpu();
                useCpuSlots += ss.get_num_used_cpu();
                
                totalMemSlots += ss.get_num_mem();
                useMemSlots += ss.get_num_used_mem();
                
                totalDiskSlots += ss.get_num_disk();
                useDiskSlots += ss.get_num_used_disk();
                
                totalPortSlots += ss.get_num_workers();
                usePortSlots += ss.get_num_used_workers();
            }
            
            freeCpuSlots = totalCpuSlots - useCpuSlots;
            freeMemSlots = totalMemSlots - useMemSlots;
            freeDiskSlots = totalDiskSlots - useDiskSlots;
            freePortSlots = totalPortSlots - usePortSlots;
        }

        // "Running tasks"
        int totalTasks = 0;
        List<TopologySummary> topos = summ.get_topologies();
        if (topos != null) {
            int topoSize = topos.size();
            for (int j = 0; j < topoSize; j++) {
                totalTasks += topos.get(j).get_num_tasks();
            }
            
        }
        
        String nimbustime = StatBuckets.prettyUptimeStr(summ
                .get_nimbus_uptime_secs());
        
        
        List<ClusterSumm> clusumms = new ArrayList<ClusterSumm>();
        
        
        ClusterSumm clusterSumm = new ClusterSumm();
        clusterSumm.setNimbusHostname(masterHostname);
        clusterSumm.setNimbusUptime(nimbustime);
        clusterSumm.setSupervisorNum(String.valueOf(supSize));
        clusterSumm.setRunningTaskNum(String.valueOf(totalTasks));
        
        clusterSumm.setTotalCpuSlotNum(String.valueOf(totalCpuSlots));
        clusterSumm.setUsedCpuSlotNum(String.valueOf(useCpuSlots));
        clusterSumm.setFreeCpuSlotNum(String.valueOf(freeCpuSlots));
        
        clusterSumm.setTotalMemSlotNum(String.valueOf(totalMemSlots));
        clusterSumm.setUsedMemSlotNum(String.valueOf(useMemSlots));
        clusterSumm.setFreeMemSlotNum(String.valueOf(freeMemSlots));
        
        clusterSumm.setTotalDiskSlotNum(String.valueOf(totalDiskSlots));
        clusterSumm.setUsedDiskSlotNum(String.valueOf(useDiskSlots));
        clusterSumm.setFreeDiskSlotNum(String.valueOf(freeDiskSlots));
        
        clusterSumm.setTotalPortSlotNum(String.valueOf(totalPortSlots));
        clusterSumm.setUsedPortSlotNum(String.valueOf(usePortSlots));
        clusterSumm.setFreePortSlotNum(String.valueOf(freePortSlots));
        
        clusumms.add(clusterSumm);
        return clusumms;
    }
    
    /**
     * Convert thrift SupervisorSummary to UI bean SupervisorSumm
     * @param ss
     * @return
     */
    public static List<SupervisorSumm> supervisorSummary(
            List<SupervisorSummary> ss) {
        // uptime host slots usedslots
        
        List<SupervisorSumm> ssumm = new ArrayList<SupervisorSumm>();
        
        if (ss == null) {
            ss = new ArrayList<SupervisorSummary>();
        }
        
        for (SupervisorSummary s : ss) {
            SupervisorSumm ssum = new SupervisorSumm(s);
            
            ssumm.add(ssum);
        }
        
        return ssumm;
    }
    
    public static Map readUiConfig()  {
        Map ret = Utils.readStormConfig();
        String curDir = System.getProperty("user.home");
        String confPath = curDir + File.separator + ".jstorm" + 
                File.separator + "storm.yaml";
        File file = new File(confPath);
        if (file.exists()) {
        
            FileInputStream fileStream;
            try {
                fileStream = new FileInputStream(file);
                Yaml yaml = new Yaml();
                
                Map clientConf = (Map)yaml.load(fileStream);
                if (clientConf != null) {
                    ret.putAll(clientConf);
                }
            } catch (FileNotFoundException e) {
                // TODO Auto-generated catch block
            }
            
        }
        if (ret.containsKey(Config.NIMBUS_HOST) == false) {
            ret.put(Config.NIMBUS_HOST, "localhost");
            
        }
        return ret;
    }
    

    public static void main(String[] args) {
    }
}
