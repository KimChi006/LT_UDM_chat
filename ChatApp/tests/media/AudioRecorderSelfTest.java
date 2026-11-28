package media;

import java.io.IOException;

public class AudioRecorderSelfTest {

    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        testQuietRecordingIsAmplified();
        testSilentRecordingIsRejected();
        System.out.println("RESULT: passed=" + passed + ", failed=" + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testQuietRecordingIsAmplified() throws Exception {
        byte[] samples = new byte[]{(byte) 0xE8, 0x03, 0x0C, (byte) 0xFE};
        AudioRecorder.normalizePcm(samples);
        int positive = readSample(samples, 0);
        int negative = readSample(samples, 2);
        check("quiet PCM is amplified without changing polarity",
                positive == 24576 && negative == -12288);
    }

    private static void testSilentRecordingIsRejected() {
        try {
            AudioRecorder.normalizePcm(new byte[8]);
            check("silent PCM is rejected", false);
        } catch (IOException expected) {
            check("silent PCM is rejected", true);
        }
    }

    private static int readSample(byte[] samples, int offset) {
        return (short) ((samples[offset + 1] << 8) | (samples[offset] & 0xFF));
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("PASS  " + name);
        } else {
            failed++;
            System.out.println("FAIL  " + name);
        }
    }
}
