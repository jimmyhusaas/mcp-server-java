package com.jimmy.mcp.tools;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for the news search tool.
 *
 * <p>Configure one or more RSS feed URLs in {@code application.properties}:
 * <pre>
 * mcp.tools.news.rss-urls[0]=https://news.ltn.com.tw/rss/all.xml
 * mcp.tools.news.rss-urls[1]=https://news.pts.org.tw/rss.xml
 * </pre>
 *
 * <p>Results from all configured sources are aggregated and deduplicated before
 * being returned to the AI. If no URLs are configured, the default is 自由時報.
 */
@ConfigurationProperties(prefix = "mcp.tools.news")
public class NewsToolProperties {

    /**
     * List of RSS feed URLs to search. Defaults to 自由時報 if not configured.
     */
    private List<String> rssUrls = new ArrayList<>(
            List.of("https://news.ltn.com.tw/rss/all.xml")
    );

    public List<String> getRssUrls() {
        return rssUrls;
    }

    public void setRssUrls(List<String> rssUrls) {
        this.rssUrls = rssUrls;
    }
}
