package np.com.sudanchapagain.youtube_playlist_time_counter;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.*;

import org.json.*;

public class Main {
    private static final String API_KEY = "";
    private static final String API_URL = "https://www.googleapis.com/youtube/v3/playlistItems";
    private static final String VIDEO_DETAILS_URL = "https://www.googleapis.com/youtube/v3/videos";
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    public static String extractPlaylistID(String url) {
        Pattern pattern = Pattern.compile("[?&]list=([a-zA-Z0-9_-]+)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    public static List<String> fetchPlaylistItems(String playlistID) throws IOException, InterruptedException {
        List<String> videoIDs = new ArrayList<>();
        String nextPageToken = "";

        do {
            String uri = API_URL + "?part=contentDetails&playlistId=" + playlistID + "&maxResults=50&pageToken=" + nextPageToken + "&key=" + API_KEY;

            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(uri)).GET().build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to fetch playlist items");
            }

            JSONObject json = new JSONObject(response.body());
            JSONArray items = json.getJSONArray("items");

            for (int i = 0; i < items.length(); i++) {
                String videoId = items.getJSONObject(i).getJSONObject("contentDetails").getString("videoId");
                videoIDs.add(videoId);
            }

            nextPageToken = json.optString("nextPageToken", "");

        } while (!nextPageToken.isEmpty());

        return videoIDs;
    }

    public static Duration parseISO8601Duration(String duration) {
        Pattern pattern = Pattern.compile("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?");
        Matcher matcher = pattern.matcher(duration);
        if (matcher.matches()) {
            int hours = matcher.group(1) != null ? Integer.parseInt(matcher.group(1)) : 0;
            int minutes = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;
            int seconds = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;
            return Duration.ofHours(hours).plusMinutes(minutes).plusSeconds(seconds);
        }
        return Duration.ZERO;
    }

    public static Duration fetchVideoDuration(String videoID) throws IOException, InterruptedException {
        String uri = VIDEO_DETAILS_URL + "?part=contentDetails&id=" + videoID + "&key=" + API_KEY;

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(uri)).GET().build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to fetch video details");
        }

        JSONObject json = new JSONObject(response.body());
        JSONArray items = json.getJSONArray("items");
        if (items.isEmpty()) {
            throw new RuntimeException("No video details found for ID " + videoID);
        }

        String isoDuration = items.getJSONObject(0).getJSONObject("contentDetails").getString("duration");
        return parseISO8601Duration(isoDuration);
    }

    public static String formatDuration(Duration duration) {
        long totalSeconds = duration.getSeconds();

        long days = totalSeconds / (24 * 3600);
        long hours = (totalSeconds % (24 * 3600)) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0 || days > 0) sb.append(hours).append("h ");
        if (minutes > 0 || hours > 0 || days > 0) sb.append(minutes).append("m ");
        sb.append(seconds).append("s");

        return sb.toString().trim();
    }

    public static void printDurationAtSpeed(Duration totalDuration, double speed) {
        Duration adjusted = Duration.ofSeconds((long) (totalDuration.getSeconds() / speed));
        System.out.printf("At %.2fx: %s%n", speed, formatDuration(adjusted));
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java callHistory <playlist_url>");
            return;
        }

        String playlistURL = args[0];
        String playlistID = extractPlaylistID(playlistURL);

        if (playlistID.isEmpty()) {
            System.err.println("Invalid YouTube playlist URL.");
            return;
        }

        System.out.println("\nFetching playlist: " + playlistID + "...");

        try {
            List<String> videoIDs = fetchPlaylistItems(playlistID);

            Duration totalDuration = Duration.ZERO;
            for (String videoID : videoIDs) {
                try {
                    totalDuration = totalDuration.plus(fetchVideoDuration(videoID));
                } catch (Exception e) {
                    System.err.println("Error fetching duration for " + videoID + ": " + e.getMessage());
                }
            }

            System.out.println("\nTotal duration: " + formatDuration(totalDuration) + "\n");

            printDurationAtSpeed(totalDuration, 1.25);
            printDurationAtSpeed(totalDuration, 1.5);
            printDurationAtSpeed(totalDuration, 1.75);
            printDurationAtSpeed(totalDuration, 2.0);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
