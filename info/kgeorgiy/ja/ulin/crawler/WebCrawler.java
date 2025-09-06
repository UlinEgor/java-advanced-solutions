package info.kgeorgiy.ja.ulin.crawler;

import info.kgeorgiy.java.advanced.crawler.*;
import info.kgeorgiy.java.advanced.crawler.CachingDownloader;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class WebCrawler implements AdvancedCrawler {
    private final Downloader downloader;
    private final ExecutorService downloaders;
    private final ExecutorService extractors;
    // :NOTE: never cleaned
    private final Map<String, Semaphore> hostLimits;
    private final int perHost;

    private final static int DEPTH_DEFAULT = 1;
    private final static int DOWNLOADERS_DEFAULT = Integer.MAX_VALUE;
    private final static int EXTRACTORS_DEFAULT = Integer.MAX_VALUE;
    private final static int PER_HOST_DEFAULT = Integer.MAX_VALUE;
    private final static int TIME_SCALE = 10;
    private final static int TIMEOUT = 20;

    /**
     * Run WebCrawler, that bypasses and downloads pages from the internet.
     * Start from {@code url} and go with parallel bfs.
     *
     * @param args "url [depth [downloaders [extractors [perHost]]]]"
     */
    public static void main(String[] args) {
        if (args == null || args.length < 1) {
            System.err.println("Not enough argument: expected \"url [depth [downloaders [extractors [perHost]]]]\"");
            return;
        }

        String url;
        int depth = DEPTH_DEFAULT;
        int downloaders = DOWNLOADERS_DEFAULT;
        int extractors = EXTRACTORS_DEFAULT;
        int perHost = PER_HOST_DEFAULT;

        url = args[0];
        if (args.length >= 2) {
            depth = Integer.parseInt(args[1]);
        }

        if (args.length >= 3) {
            downloaders = Integer.parseInt(args[2]);
        }

        if (args.length >= 4) {
            extractors = Integer.parseInt(args[3]);
        }

        if (args.length >= 5) {
            perHost = Integer.parseInt(args[4]);
        }

        try (WebCrawler crawler = new WebCrawler(new CachingDownloader(TIME_SCALE), downloaders, extractors, perHost)) {
            crawler.download(url, depth);
        } catch (IOException e) {
            System.err.println("Error in downloader: " + e.getMessage());
        }
    }

    public WebCrawler(final Downloader downloader, final int downloaders, final int extractors, final int perHost) {
        this.downloader = downloader;
        this.downloaders = Executors.newFixedThreadPool(downloaders);
        this.extractors = Executors.newFixedThreadPool(extractors);
        this.hostLimits = new ConcurrentHashMap<>();
        this.perHost = perHost;
    }

    @Override
    public Result download(final String url, final int depth, final List<String> excludes) {
        CustomResult result = new CustomResult();
        downloadLayer(
                List.of(url),
                depth,
                (host) -> excludes.stream().anyMatch(host::contains),
                Collections.newSetFromMap(new ConcurrentHashMap<>()),
                result
        );

        return new Result(result.getDownloaded(), result.getErrors());
    }

    @Override
    public Result advancedDownload(final String url, final int depth, final List<String> hosts) {
        CustomResult result = new CustomResult();
        Set<String> set = hosts.stream().collect(Collectors.toSet());
        downloadLayer(List.of(url), depth, set::contains, Collections.newSetFromMap(new ConcurrentHashMap<>()), result);

        return new Result(result.getDownloaded(), result.getErrors());
    }

    @Override
    public void close() {
        downloaders.shutdownNow();
        extractors.shutdownNow();
//         :NOTE: some logic about awaiting termination

        await(downloaders, "downloaders");
        await(extractors, "extractors");
    }

    private static void await(ExecutorService service, String name) {
        try {
            if (!service.awaitTermination(TIMEOUT, TimeUnit.SECONDS)) {
                System.err.println("Service " + name + " didn't terminated in " + TIMEOUT + " seconds.");
            }
        } catch (InterruptedException e) {
            System.err.println("Interrupt while waiting to terminate");
        }
    }

    private void downloadLayer(
            final List<String> urls,
            final int depth,
            final Function<String, Boolean> isBanned,
            final Set<String> used,
            final CustomResult result) {
        if (depth == 0) {
            return;
        }

        List<String> nextLayer = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(urls.size());

        for (String url : urls) {
            String host = getHostName(url, result, isBanned, used);

            if (host.isEmpty()) {
                latch.countDown();
                continue;
            }

            downloaders.submit(() -> {
                try {
                    Document curDocument = download(host, url);
                    result.add(url);

                    extractors.submit(() -> {
                        try {
                            List<String> links = curDocument.extractLinks();
                            nextLayer.addAll(links);
                        } catch (IOException ignore) {
                        } finally {
                            latch.countDown();
                        }
                    });
                } catch (IOException e) {
                    result.putError(url, e);
                    latch.countDown();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    latch.countDown();
                }
            });

            // :NOTE: nope. Extractor blocks when can work
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        hostLimits.clear();

        downloadLayer(nextLayer, depth - 1, isBanned, used, result);
    }

    private static String getHostName(final String url, final CustomResult result, final Function<String, Boolean> isBanned, final Set<String> used) {
        if (!used.add(url)) {
            return "";
        }

        String host;

        try {
            host = String.valueOf(URLUtils.getHost(url));
        } catch (MalformedURLException e) {
            result.putError(url, e);
            return "";
        }

        if (isBanned.apply(host)) {
            return "";
        }

        return host;
    }

    private static class CustomResult {
        private final List<String> downloaded = Collections.synchronizedList(new ArrayList<>());
        private final Map<String, IOException> errors = new ConcurrentHashMap<>();

        public void add(final String newDownloaded) {
            downloaded.add(newDownloaded);
        }

        public void putError(final String url, final IOException error) {
            errors.put(url, error);
        }

        public List<String> getDownloaded() {
            return downloaded;
        }

        public Map<String, IOException> getErrors() {
            return errors;
        }
    }

    private Document download(final String host, final String url) throws IOException, InterruptedException {
        // :NOTE: do not wait semaphore try to make useful work
        Semaphore semaphore = hostLimits.computeIfAbsent(host, _ -> new Semaphore(perHost));
        semaphore.acquire();

        try {
            return downloader.download(url);
        } finally {
            semaphore.release();
        }
    }
}
