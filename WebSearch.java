import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebSearch {
    private final Set<String> visitedLinks = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<String, String> linkLabels = new ConcurrentHashMap<>();

    public static void main(String[] args) throws InterruptedException {
        String startUrl = "https://orf.at/";
        WebSearch webSearch = new WebSearch();
        webSearch.search(startUrl);
        webSearch.outputResults();
    }

    public void search(String startUrl) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        this.crawlPage(startUrl, executor);
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.MINUTES);
    }

    private void crawlPage(String url, ExecutorService executor) {
        if (visitedLinks.add(url)) {
            executor.submit(() -> {
                try {
                    String content = this.fetchPageContent(url);
                    List<String> links = this.extractLinks(content, getDomain(url));
                    for (String link : links) {
                        crawlPage(link, executor);
                    }
                } catch (Exception e) {
                    System.err.println("Error " + url + ": " + e.getMessage());
                }
            });
        }
    }

    private String fetchPageContent(String urlString) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);

        try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            StringBuilder content = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            return content.toString();
        }
    }

    private List<String> extractLinks(String content, String domain) {
        List<String> links = new ArrayList<>();
        Pattern pattern = Pattern.compile("href=\\\"(http[s]?://[^\"]*)\\\"");
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String link = matcher.group(1);
            if (link.contains(domain)) {
                String label = link.substring(link.lastIndexOf("/") + 1);
                links.add(link);
                linkLabels.put(link, label);
            }
        }
        return links;
    }

    private String getDomain(String url) {
        try {
            return new URL(url).getHost();
        } catch (Exception e) {
            return "";
        }
    }

    private void outputResults() {
        this.linkLabels.forEach(
                (key, value1) -> System.out.println(value1 + ": " + key));
    }
}
