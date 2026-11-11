package com.aiclipmaster.app;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class AudioExtractor {

    public static class AudioData {
        public final float[] samples;
        public final int sampleRate;

        public AudioData(float[] samples, int sampleRate) {
            this.samples = samples;
            this.sampleRate = sampleRate;
        }
    }

    public static AudioData extractAudio(
            Context context,
            Uri videoUri
    ) throws Exception {

        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;

        try {
            extractor.setDataSource(context, videoUri, null);

            int track = -1;
            MediaFormat format = null;

            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat candidate = extractor.getTrackFormat(i);
                String mime = candidate.getString(MediaFormat.KEY_MIME);

                if (mime != null && mime.startsWith("audio/")) {
                    track = i;
                    format = candidate;
                    break;
                }
            }

            if (track < 0 || format == null) {
                throw new IOException("Video mein audio track nahi mila.");
            }

            extractor.selectTrack(track);

            String mime = format.getString(MediaFormat.KEY_MIME);
            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(format, null, null, 0);
            decoder.start();

            int sampleRate = format.getInteger(
                    MediaFormat.KEY_SAMPLE_RATE
            );

            int channels = format.getInteger(
                    MediaFormat.KEY_CHANNEL_COUNT
            );

            ByteArrayOutputStream pcmOutput =
                    new ByteArrayOutputStream();

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

            boolean inputDone = false;
            boolean outputDone = false;
            int outputChannels = channels;

            while (!outputDone) {

                if (!inputDone) {
                    int inputIndex =
                            decoder.dequeueInputBuffer(10000);

                    if (inputIndex >= 0) {
                        ByteBuffer inputBuffer =
                                decoder.getInputBuffer(inputIndex);

                        if (inputBuffer == null) {
                            throw new IOException("Decoder input unavailable.");
                        }

                        inputBuffer.clear();

                        int size = extractor.readSampleData(
                                inputBuffer, 0
                        );

                        if (size < 0) {
                            decoder.queueInputBuffer(
                                    inputIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            );
                            inputDone = true;
                        } else {
                            decoder.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    size,
                                    extractor.getSampleTime(),
                                    0
                            );
                            extractor.advance();
                        }
                    }
                }

                int outputIndex =
                        decoder.dequeueOutputBuffer(info, 10000);

                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat outputFormat =
                            decoder.getOutputFormat();

                    if (outputFormat.containsKey(
                            MediaFormat.KEY_CHANNEL_COUNT)) {
                        outputChannels = outputFormat.getInteger(
                                MediaFormat.KEY_CHANNEL_COUNT
                        );
                    }

                    if (outputFormat.containsKey(
                            MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = outputFormat.getInteger(
                                MediaFormat.KEY_SAMPLE_RATE
                        );
                    }
                } else if (outputIndex >= 0) {

                    ByteBuffer outputBuffer =
                            decoder.getOutputBuffer(outputIndex);

                    if (outputBuffer != null && info.size > 0) {
                        outputBuffer.position(info.offset);
                        outputBuffer.limit(info.offset + info.size);

                        byte[] chunk = new byte[info.size];
                        outputBuffer.get(chunk);
                        pcmOutput.write(chunk);
                    }

                    decoder.releaseOutputBuffer(outputIndex, false);

                    if ((info.flags &
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true;
                    }
                }
            }

            if (sampleRate <= 0 || outputChannels <= 0) {
                throw new IOException("Invalid decoded audio format.");
            }

            byte[] pcmBytes = pcmOutput.toByteArray();

            int bytesPerSample = 2;
            int frameSize = bytesPerSample * outputChannels;
            int frames = pcmBytes.length / frameSize;

            ByteBuffer buffer = ByteBuffer.wrap(pcmBytes)
                    .order(ByteOrder.LITTLE_ENDIAN);

            float[] mono = new float[frames];

            for (int i = 0; i < frames; i++) {
                float sum = 0;

                for (int c = 0; c < outputChannels; c++) {
                    sum += buffer.getShort() / 32768.0f;
                }

                mono[i] = sum / outputChannels;
            }

            return new AudioData(
                    resample(mono, sampleRate, 16000),
                    16000
            );

        } finally {
            if (decoder != null) {
                try {
                    decoder.stop();
                } catch (Exception ignored) {
                }

                decoder.release();
            }

            extractor.release();
        }
    }

    private static float[] resample(
            float[] input,
            int inputRate,
            int outputRate
    ) {
        if (input.length == 0 || inputRate <= 0) {
            return new float[0];
        }

        int outputLength = (int) (
                (long) input.length * outputRate / inputRate
        );

        float[] output = new float[outputLength];

        for (int i = 0; i < outputLength; i++) {
            double position =
                    (double) i * inputRate / outputRate;

            int left = (int) position;
            int right = Math.min(left + 1, input.length - 1);

            float fraction = (float) (position - left);

            output[i] = input[left] * (1 - fraction)
                    + input[right] * fraction;
        }

        return output;
    }
}
