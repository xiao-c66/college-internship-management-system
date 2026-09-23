package com.college.internship.service.impl;

import com.college.internship.config.SystemProperties;
import com.college.internship.security.LoginUser;
import com.college.internship.service.IPerformanceMonitorService;
import com.college.internship.service.ISysConfigService;
import com.college.internship.service.ISysOperationLogService;
import com.college.internship.vo.CacheMonitorVO;
import com.college.internship.vo.ServerMonitorVO;
import com.college.internship.vo.SlowCallRecordVO;
import com.college.internship.vo.SlowSqlMonitorVO;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * 阶段8 性能与运行时监控服务实现类 (API-116 ~ API-119)
 * 严格落实纯内存环形缓冲区 (RingBuffer)，有界容量，杜绝增加物理表
 */
@Slf4j
@Service
public class PerformanceMonitorServiceImpl implements IPerformanceMonitorService {

    private static final int DEFAULT_RING_BUFFER_CAPACITY = 1000;
    private static final int DEFAULT_SLOW_CALL_CAPACITY = 500;
    private static final long RETENTION_MILLIS = 24L * 60 * 60 * 1000; // 24小时

    private final SystemProperties systemProperties;
    private final Cache<String, String> configCaffeineCache;
    private final ISysOperationLogService operationLogService;
    private final ISysConfigService sysConfigService;

    private final BoundedRingBuffer<RequestSample> requestRingBuffer;
    private final BoundedRingBuffer<SlowCallRecordVO> slowCallRingBuffer;
    private final AtomicLong slowCallSequence = new AtomicLong(0);

    public PerformanceMonitorServiceImpl(
            SystemProperties systemProperties,
            Cache<String, String> configCaffeineCache,
            ISysOperationLogService operationLogService,
            @Lazy ISysConfigService sysConfigService) {
        this.systemProperties = systemProperties;
        this.configCaffeineCache = configCaffeineCache;
        this.operationLogService = operationLogService;
        this.sysConfigService = sysConfigService;

        int reqCapacity = (systemProperties.getSlowSql() != null && systemProperties.getSlowSql().getRingBufferSize() != null)
                ? systemProperties.getSlowSql().getRingBufferSize() : DEFAULT_RING_BUFFER_CAPACITY;
        int slowCapacity = (systemProperties.getSlowSql() != null && systemProperties.getSlowSql().getMaxStoredRecords() != null)
                ? systemProperties.getSlowSql().getMaxStoredRecords() : DEFAULT_SLOW_CALL_CAPACITY;

        this.requestRingBuffer = new BoundedRingBuffer<>(Math.min(reqCapacity, DEFAULT_RING_BUFFER_CAPACITY));
        this.slowCallRingBuffer = new BoundedRingBuffer<>(Math.min(slowCapacity, DEFAULT_SLOW_CALL_CAPACITY));
    }

    @Override
    public ServerMonitorVO getServerMetrics() {
        Runtime runtime = Runtime.getRuntime();
        long totalMem = runtime.totalMemory() / (1024 * 1024);
        long freeMem = runtime.freeMemory() / (1024 * 1024);
        long maxMem = runtime.maxMemory() / (1024 * 1024);
        long usedMem = totalMem - freeMem;
        int cpuCores = runtime.availableProcessors();

        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        double cpuLoad = 0.0;
        if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
            double load = ((com.sun.management.OperatingSystemMXBean) osBean).getCpuLoad();
            if (load >= 0) {
                cpuLoad = Math.round(load * 10000.0) / 100.0;
            }
        }

        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        long upTimeSec = runtimeMXBean.getUptime() / 1000;

        File rootFile = new File(".");
        long totalDisk = rootFile.getTotalSpace() / (1024 * 1024 * 1024);
        long freeDisk = rootFile.getFreeSpace() / (1024 * 1024 * 1024);
        long usedDisk = Math.max(0, totalDisk - freeDisk);

        return ServerMonitorVO.builder()
                .cpuCores(cpuCores)
                .systemCpuLoad(cpuLoad)
                .jvmTotalMemoryMB(totalMem)
                .jvmFreeMemoryMB(freeMem)
                .jvmUsedMemoryMB(usedMem)
                .jvmMaxMemoryMB(maxMem)
                .jvmVersion(System.getProperty("java.version"))
                .jvmHome(System.getProperty("java.home"))
                .upTimeSeconds(upTimeSec)
                .osName(System.getProperty("os.name"))
                .osArch(System.getProperty("os.arch"))
                .totalDiskSpaceGB(totalDisk)
                .freeDiskSpaceGB(freeDisk)
                .usedDiskSpaceGB(usedDisk)
                .status("UP")
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Override
    public CacheMonitorVO getCacheMetrics() {
        CacheStats stats = configCaffeineCache.stats();
        long hitCount = stats.hitCount();
        long missCount = stats.missCount();
        double hitRate = Math.round(stats.hitRate() * 10000.0) / 10000.0;
        long evictionCount = stats.evictionCount();
        long estimatedSize = configCaffeineCache.estimatedSize();
        Long maxSize = (systemProperties.getCache() != null && systemProperties.getCache().getCaffeine() != null && systemProperties.getCache().getCaffeine().getMaxSize() != null)
                ? systemProperties.getCache().getCaffeine().getMaxSize().longValue() : 1000L;
        Long expireMinutes = (systemProperties.getCache() != null && systemProperties.getCache().getCaffeine() != null)
                ? systemProperties.getCache().getCaffeine().getExpireAfterWriteMinutes() : 30L;

        return CacheMonitorVO.builder()
                .cacheName("configCaffeineCache")
                .hitCount(hitCount)
                .missCount(missCount)
                .hitRate(hitRate)
                .evictionCount(evictionCount)
                .estimatedSize(estimatedSize)
                .maxSize(maxSize)
                .expireAfterWriteMinutes(expireMinutes)
                .build();
    }

    @Override
    public void clearCache(String cacheName, LoginUser loginUser, String clientIp) {
        configCaffeineCache.invalidateAll();
        Long opId = loginUser != null ? loginUser.getUserId() : 0L;
        String opName = loginUser != null ? loginUser.getUsername() : "SYS_ADMIN";
        operationLogService.logOperation("清理系统缓存", "CACHE_CLEAR", "clearCache", "POST",
                opId, opName, "/api/v1/system/monitor/cache/clear",
                clientIp, "{\"cacheName\":\"" + (cacheName != null ? cacheName : "ALL") + "\"}",
                "{\"status\":\"CLEARED\"}", 1, null);
        log.info("管理员 [{}] 成功执行本地 Caffeine 缓存全局清理", opName);
    }

    @Override
    public SlowSqlMonitorVO getSlowSqlMetrics() {
        long thresholdMs = getSlowThresholdMs();
        long now = System.currentTimeMillis();
        long windowStart = now - RETENTION_MILLIS;

        List<RequestSample> allSamples = requestRingBuffer.toList();
        List<RequestSample> validSamples = allSamples.stream()
                .filter(s -> s.getTimestamp() >= windowStart)
                .collect(Collectors.toList());

        int count = validSamples.size();
        if (count == 0) {
            return SlowSqlMonitorVO.builder()
                    .thresholdMs(thresholdMs)
                    .ringBufferSize(requestRingBuffer.getCapacity())
                    .sampleCount(0)
                    .p95CostMs(0L)
                    .p99CostMs(0L)
                    .minCostMs(0L)
                    .maxCostMs(0L)
                    .avgCostMs(0.0)
                    .qps(0.0)
                    .slowCalls(getRecentSlowCalls(windowStart))
                    .build();
        }

        List<Long> latencies = validSamples.stream()
                .map(RequestSample::getCostMs)
                .sorted()
                .collect(Collectors.toList());

        long min = latencies.get(0);
        long max = latencies.get(count - 1);
        double sum = 0;
        for (Long l : latencies) {
            sum += l;
        }
        double avg = Math.round((sum / count) * 100.0) / 100.0;

        // 最近秩百分位数计算 Nearest Rank Algorithm
        int p95Index = Math.max(0, Math.min(count - 1, (int) Math.ceil(count * 0.95) - 1));
        long p95 = latencies.get(p95Index);

        int p99Index = Math.max(0, Math.min(count - 1, (int) Math.ceil(count * 0.99) - 1));
        long p99 = latencies.get(p99Index);

        long oldestTime = validSamples.get(0).getTimestamp();
        long newestTime = validSamples.get(count - 1).getTimestamp();
        double spanSeconds = Math.max(1.0, (newestTime - oldestTime) / 1000.0);
        double qps = Math.round((count / spanSeconds) * 100.0) / 100.0;

        return SlowSqlMonitorVO.builder()
                .thresholdMs(thresholdMs)
                .ringBufferSize(requestRingBuffer.getCapacity())
                .sampleCount(count)
                .p95CostMs(p95)
                .p99CostMs(p99)
                .minCostMs(min)
                .maxCostMs(max)
                .avgCostMs(avg)
                .qps(qps)
                .slowCalls(getRecentSlowCalls(windowStart))
                .build();
    }

    private List<SlowCallRecordVO> getRecentSlowCalls(long windowStart) {
        List<SlowCallRecordVO> list = slowCallRingBuffer.toList().stream()
                .filter(call -> call.getTimestamp() != null)
                .collect(Collectors.toList());
        Collections.reverse(list); // 最新的排在前面
        return list;
    }

    @Override
    public void recordRequest(String uri, String method, long costMs, int statusCode,
                              String operatorName, String clientIp, String params) {
        long now = System.currentTimeMillis();
        requestRingBuffer.add(new RequestSample(uri, method, costMs, statusCode, now));

        long threshold = getSlowThresholdMs();
        if (costMs >= threshold) {
            SlowCallRecordVO slowRecord = SlowCallRecordVO.builder()
                    .id(slowCallSequence.incrementAndGet())
                    .uri(uri)
                    .method(method)
                    .costMs(costMs)
                    .timestamp(LocalDateTime.now())
                    .operatorName(StringUtils.hasText(operatorName) ? operatorName : "ANONYMOUS")
                    .clientIp(StringUtils.hasText(clientIp) ? clientIp : "127.0.0.1")
                    .paramSummary(params != null ? (params.length() > 200 ? params.substring(0, 200) + "..." : params) : null)
                    .build();
            slowCallRingBuffer.add(slowRecord);
            log.warn("检测到慢调用告警: URI={}, 耗时={}ms (基准阈值={}ms)", uri, costMs, threshold);
        }
    }

    @Override
    public void clearSamplesForTesting() {
        requestRingBuffer.clear();
        slowCallRingBuffer.clear();
        slowCallSequence.set(0);
    }

    @Override
    public long getSlowThresholdMs() {
        try {
            if (sysConfigService != null) {
                String val = sysConfigService.getConfigValue("system.slow-sql-threshold-ms");
                if (StringUtils.hasText(val)) {
                    return Long.parseLong(val.trim());
                }
            }
        } catch (Exception ignored) {
        }
        if (systemProperties.getSlowSql() != null && systemProperties.getSlowSql().getThresholdMs() != null) {
            return systemProperties.getSlowSql().getThresholdMs();
        }
        return 500L;
    }

    @Data
    @AllArgsConstructor
    public static class RequestSample {
        private String uri;
        private String method;
        private long costMs;
        private int statusCode;
        private long timestamp;
    }

    /**
     * 真正有界的环形缓冲区实现 (Fixed-capacity array)
     * 杜绝动态扩容，物理上限由 capacity 锁定，超过容量后覆盖最旧元素
     */
    public static class BoundedRingBuffer<T> {
        private final Object[] array;
        private final int capacity;
        private int head = 0;
        private int tail = 0;
        private int count = 0;
        private final ReentrantLock lock = new ReentrantLock();

        public BoundedRingBuffer(int capacity) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("RingBuffer capacity must be positive");
            }
            this.capacity = capacity;
            this.array = new Object[capacity];
        }

        public void add(T item) {
            lock.lock();
            try {
                array[tail] = item;
                tail = (tail + 1) % capacity;
                if (count < capacity) {
                    count++;
                } else {
                    head = (head + 1) % capacity; // 覆盖最旧元素
                }
            } finally {
                lock.unlock();
            }
        }

        @SuppressWarnings("unchecked")
        public List<T> toList() {
            lock.lock();
            try {
                List<T> list = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    int index = (head + i) % capacity;
                    list.add((T) array[index]);
                }
                return list;
            } finally {
                lock.unlock();
            }
        }

        public int size() {
            lock.lock();
            try {
                return count;
            } finally {
                lock.unlock();
            }
        }

        public int getCapacity() {
            return capacity;
        }

        public void clear() {
            lock.lock();
            try {
                Arrays.fill(array, null);
                head = 0;
                tail = 0;
                count = 0;
            } finally {
                lock.unlock();
            }
        }
    }
}
