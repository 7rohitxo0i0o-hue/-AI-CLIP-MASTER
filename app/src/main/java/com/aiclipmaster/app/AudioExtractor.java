package com.aiclipmaster.app;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

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

            int audioTrack = -1;
            MediaFormat format = null;

            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);

                if (mime != null && mime.startsWith("audio/")) {
                    audioTrack = i;
                    format = f;
                    break;
                }
            }

            if (audioTrack == -1 || format == null) {
                throw new IOException("Audio track not found.");
            }

            extractor.selectTrack(audioTrack);

            String mime = format.getString(MediaFormat.KEY_MIME);
            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(format, null, null, 0);
            decoder.start();

            int inputRate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                    ? format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    : 44100;

            int channels = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                    ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    : 1;

            java.io.ByteArrayOutputStream output =
                    new java.io.ByteArrayOutputStream();

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;

            while (!outputDone) {

                if (!inputDone) {
                    int inputIndex = decoder.dequeueInputBuffer(10000);

                    if (inputIndex >= 0) {
                        ByteBuffer inputBuffer =
                                decoder.getInputBuffer(inputIndex);

                        if (inputBuffer != null) {
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
                                long time = extractor.getSampleTime();

                                decoder.queueInputBuffer(
                                        inputIndex, 0, size, time, 0
                                );

                                extractor.advance();
                            }
                        }
                    }
                }

                int outputIndex = decoder.dequeueOutputBuffer(info, 10000);

                if (outputIndex >= 0) {
                    ByteBuffer buffer =
                            decoder.getOutputBuffer(outputIndex);

                    if (buffer != null && info.size > 0) {
                        buffer.position(info.offset);
                        buffer.limit(info.offset + info.size);

                        byte[] data = new byte[info.size];
                        buffer.get(data);
                        output.write(data);
                    }

                    decoder.releaseOutputBuffer(outputIndex, false);

                    if ((info.flags &
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true;
                    }
                }
            }

            byte[] pcmBytes = output.toByteArray();

            ByteBuffer pcmBuffer = ByteBuffer.wrap(pcmBytes)
                    .order(ByteOrder.LITTLE_ENDIAN);

            int totalSamples = pcmBytes.length / 2;
            int frames = totalSamples / channels;

            float[] mono = new float[frames];

            for (int i = 0; i < frames; i++) {
                float sum = 0;

                for (int c = 0; c < channels; c++) {
                    short sample = pcmBuffer.getShort();
                    sum += sample / 32768.0f;
                }

                mono[i] = sum / channels;
            }

            float[] resampled = resample(
                    mono, inputRate, 16000
            );

            return new AudioData(resampled, 16000);

        } finally {
            if (decoder != null) {
                try {
                    decoder.stop();
                    decoder.release();
                } catch (Exception ignored) {}
            }

            extractor.release();
        }
    }

    private static float[] resample(
            float[] input,
            int inputRate,
            int outputRate
    ) {
        if (inputRate <= 0 || input.length == 0) {
            return new float[0];
        }

        int outputLength =
                (int) ((long) input.length * outputRate / inputRate);

        float[] output = new float[outputLength];

        for (int i = 0; i < outputLength; i++) {
            double position = (double) i * inputRate / outputRate;

            int left = (int) position;
            int right = Math.min(left + 1, input.length - 1);

            float fraction = (float) (position - left);

            output[i] = input[left] * (1 - fraction)
                    + input[right] * fraction;
        }

        return output;
    }
}
