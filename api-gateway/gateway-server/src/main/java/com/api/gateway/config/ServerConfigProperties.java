package com.api.gateway.config;

import com.api.gateway.spi.balance.RandomLoadBalance;
import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties(prefix = "api.gateway")
public class ServerConfigProperties {
    /**
     * 负载均衡算法，默认轮询
     */
    private String loadBalance = RandomLoadBalance.NAME;
    /**
     * 网关超时时间，默认3s
     */
    private Long timeOutMillis = 3000L;
    /**
     * 缓存刷新间隔，默认10s
     */
    private Long cacheRefreshInterval = 10L;

    /**
     * 限流方式QPS或THREAD，默认QPS
     */
    private String rateLimitType = "qps";
    /**
     * 限流数量
     */
    private Integer rateLimitCount;


    public String getRateLimitType() {
        return rateLimitType;
    }

    public void setRateLimitType(String rateLimitType) {
        this.rateLimitType = rateLimitType;
    }

    public Integer getRateLimitCount() {
        return rateLimitCount;
    }

    public void setRateLimitCount(Integer rateLimitCount) {
        this.rateLimitCount = rateLimitCount;
    }

    public Long getCacheRefreshInterval() {
        return cacheRefreshInterval;
    }

    public void setCacheRefreshInterval(Long cacheRefreshInterval) {
        this.cacheRefreshInterval = cacheRefreshInterval;
    }

    public Long getTimeOutMillis() {
        return timeOutMillis;
    }

    public void setTimeOutMillis(Long timeOutMillis) {
        this.timeOutMillis = timeOutMillis;
    }

    public String getLoadBalance() {
        return loadBalance;
    }

    public void setLoadBalance(String loadBalance) {
        this.loadBalance = loadBalance;
    }


}
