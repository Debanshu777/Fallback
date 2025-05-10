package com.debanshu777.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class SearchService {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    /**
     * Perform a search query and return formatted HTML content
     */
    suspend fun search(query: String): String {
        logger.info("Performing search for: $query")


        return try {
            withContext(Dispatchers.IO) {
                // Encode the query for URL safety
                val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())

                // In a real implementation, you'd connect to a search API or scrape search results
                // For this example, we'll use a simple approach with DuckDuckGo's HTML site
                // Note: In production, respect robots.txt and terms of service
                val url = "https://lite.duckduckgo.com/lite/?q=$encodedQuery"

                // Fetch the search results page
                val document = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (compatible; FallbackEmergencyBrowser/1.0)")
                    .timeout(10000)
                    .get()

                // Extract search results
                val searchResults = document.select("a.result-link")

                // Build a simple HTML response
                buildSearchResultsHtml(query, searchResults.take(5).map {
                    ResultItem(
                        title = it.text(),
                        url = it.attr("href"),
                        description = it.parent()?.parent()?.select("td.result-snippet")?.text() ?: ""
                    )
                })
            }
        } catch (e: Exception) {
            logger.error("Error performing search: ${e.message}", e)
            buildErrorHtml(query, e.message ?: "Unknown error")
        }
    }

    /**
     * Get content from a specific URL
     */
    suspend fun getContent(url: String): String {
        logger.info("Fetching content from: $url")

        return try {
            // Fetch the page content
            val document = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (compatible; FallbackEmergencyBrowser/1.0)")
                .timeout(10000)
                .get()

            // Extract the main content and simplify it for SMS transmission
            val title = document.title()
            val content = document.select("p").take(5).joinToString("") { it.html() }

            buildContentHtml(title, url, content)
        } catch (e: Exception) {
            logger.error("Error fetching content: ${e.message}", e)
            buildErrorHtml("Content fetch", e.message ?: "Unknown error")
        }
    }

    /**
     * Build HTML for search results
     */
    private fun buildSearchResultsHtml(query: String, results: List<ResultItem>): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Search: $query</title>
                <style>
                    body { font-family: Arial, sans-serif; margin: 8px; }
                    h1 { font-size: 18px; }
                    .result { margin-bottom: 12px; }
                    .result a { color: #1a0dab; text-decoration: none; font-weight: bold; }
                    .result .url { color: #006621; font-size: 12px; margin-bottom: 4px; }
                    .result .description { font-size: 14px; }
                </style>
            </head>
            <body>
                <h1>Results for: $query</h1>
                <div class="results">
                    ${results.joinToString("") {
                        """
                        <div class="result">
                            <a href="${it.url}">${it.title}</a>
                            <div class="url">${it.url}</div>
                            <div class="description">${it.description}</div>
                        </div>
                        """
                    }}
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Build HTML for a specific content page
     */
    private fun buildContentHtml(title: String, url: String, content: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>$title</title>
                <style>
                    body { font-family: Arial, sans-serif; margin: 8px; }
                    h1 { font-size: 18px; }
                    .source { color: #006621; font-size: 12px; margin-bottom: 12px; }
                    .content { font-size: 14px; }
                </style>
            </head>
            <body>
                <h1>$title</h1>
                <div class="source">Source: $url</div>
                <div class="content">$content</div>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Build HTML for error messages
     */
    private fun buildErrorHtml(query: String, errorMessage: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Error</title>
                <style>
                    body { font-family: Arial, sans-serif; margin: 8px; }
                    h1 { font-size: 18px; color: #d32f2f; }
                    .message { font-size: 14px; }
                </style>
            </head>
            <body>
                <h1>Error searching for: $query</h1>
                <div class="message">$errorMessage</div>
            </body>
            </html>
        """.trimIndent()
    }
}

data class ResultItem(
    val title: String,
    val url: String,
    val description: String
)