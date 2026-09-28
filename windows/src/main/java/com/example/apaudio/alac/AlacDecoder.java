package com.example.apaudio.alac;

/** Minimal reusable wrapper around the vendored pure-Java ALAC decoder core. */
public final class AlacDecoder {
    private final AlacFile decoder;
    private final int frameLength;
    private final int channels;
    private final int bitDepth;

    public AlacDecoder(int[] fmtp) {
        if (fmtp.length != 12) {
            throw new IllegalArgumentException("ALAC fmtp must contain payload type plus 11 values");
        }
        frameLength = fmtp[1];
        bitDepth = fmtp[3];
        channels = fmtp[7];
        if (frameLength <= 0 || bitDepth != 16 || channels != 2) {
            throw new IllegalArgumentException("Only observed 16-bit stereo ALAC is supported");
        }

        decoder = AlacFile.create(bitDepth, channels);
        decoder.setInfo_maxSamplesPerFrame = frameLength;
        decoder.setInfo_7A = fmtp[2];
        decoder.setInfo_sampleSize = bitDepth;
        decoder.setInfo_riceHistoryMult = fmtp[4];
        decoder.setInfo_riceInitialHistory = fmtp[5];
        decoder.setInfo_riceKModifier = fmtp[6];
        decoder.setInfo_7f = channels;
        decoder.setInfo_80 = fmtp[8];
        decoder.setInfo_82 = fmtp[9];
        decoder.setInfo_86 = fmtp[10];
        decoder.setInfo_8a_rate = fmtp[11];
    }

    public int decodeFrame(byte[] encodedFrame, int[] outputSamples) {
        if (outputSamples.length < frameLength * channels) {
            throw new IllegalArgumentException("PCM output buffer is too small");
        }
        return decoder.decodeFrame(encodedFrame, outputSamples, outputSamples.length * 2);
    }
}
