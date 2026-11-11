
package com.aiclipmaster.app;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import java.io.IOException;

public class AudioExtractor {

    public static MediaFormat findAudioTrack(
            Context context,
            Uri videoUri,
            MediaExtractor extractor
    ) throws IOException {

        extractor.setDataSource(
                context,
                videoUri,
                null
        );

        for (int i = 0; i < extractor.getTrackCount(); i++) {

            MediaFormat format = extractor.getTrackFormat(i);

            String mime = format.getString(
                    MediaFormat.KEY_MIME
            );

            if (mime != null && mime.startsWith("audio/")) {
                extractor.selectTrack(i);
                return format;
            }
        }

        throw new IOException("Video mein audio track nahi mila.");
    }
}

