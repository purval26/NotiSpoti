import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class test_pathfinder {
    public static void main(String[] args) throws Exception {
        // Let's run SpotifyTrackResolver directly and see what happens!
        com.notispoti.app.SpotifyTrackResolver resolver = new com.notispoti.app.SpotifyTrackResolver();
        resolver.resolve("Na Na Na Na - J Star", new com.notispoti.app.SpotifyTrackResolver.Callback() {
            public void onSuccess(com.notispoti.app.SpotifyTrackResolver.TrackInfo track) {
                System.out.println("SUCCESS: " + track.name + " -> " + track.uri);
                System.exit(0);
            }
            public void onError(String msg) {
                System.out.println("ERROR: " + msg);
                System.exit(1);
            }
        });
        Thread.sleep(10000);
    }
}
