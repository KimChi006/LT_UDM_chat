package media;

import model.PacketType;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

public final class AudioRecorder {

    private static final int MAX_RECORDING_BYTES = 16 * 1024 * 1024;
    private final AudioFormat format = new AudioFormat(44100, 16, 1, true, false);
    private final ByteArrayOutputStream audio = new ByteArrayOutputStream();
    private TargetDataLine line;
    private Thread captureThread;
    private volatile boolean recording;
    private volatile IOException captureFailure;

    public synchronized void start() throws LineUnavailableException {
        if (captureThread != null) {
            throw new IllegalStateException("Hãy kết thúc bản ghi âm hiện tại trước.");
        }
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        TargetDataLine newLine = (TargetDataLine) AudioSystem.getLine(info);
        try {
            newLine.open(format);
            newLine.start();
        } catch (LineUnavailableException exception) {
            closeLine(newLine);
            throw exception;
        } catch (RuntimeException exception) {
            closeLine(newLine);
            throw exception;
        }
        synchronized (audio) {
            audio.reset();
        }
        line = newLine;
        captureFailure = null;
        recording = true;
        captureThread = new Thread(() -> capture(newLine), "AudioRecorder");
        captureThread.setDaemon(true);
        captureThread.start();
    }

    public File stopAndSave() throws IOException {
        TargetDataLine activeLine;
        Thread activeThread;
        synchronized (this) {
            if (captureThread == null) {
                throw new IOException("Không có bản ghi âm đang chạy.");
            }
            recording = false;
            activeLine = line;
            activeThread = captureThread;
        }
        closeLine(activeLine);
        try {
            activeThread.join(2000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Ghi âm bị gián đoạn.", exception);
        }
        if (activeThread.isAlive()) {
            throw new IOException("Thiết bị âm thanh không dừng ghi âm.");
        }

        IOException failure;
        byte[] bytes = null;
        synchronized (this) {
            if (captureThread == activeThread) {
                synchronized (audio) {
                    bytes = audio.toByteArray();
                }
                captureThread = null;
                line = null;
            }
            failure = captureFailure;
            captureFailure = null;
        }
        if (failure != null) {
            throw failure;
        }

        if (bytes == null) {
            throw new IOException("Không thể hoàn tất bản ghi âm.");
        }
        if (bytes.length == 0) {
            throw new IOException("Không ghi được âm thanh.");
        }
        normalizePcm(bytes);
        File folder = MediaManager.getFolder(PacketType.SOUND);
        if (!folder.isDirectory() && !folder.mkdirs()) {
            throw new IOException("Không thể tạo thư mục âm thanh.");
        }
        File output = new File(folder, "ghi-am-" + UUID.randomUUID() + ".wav");
        try (AudioInputStream input = new AudioInputStream(
                new ByteArrayInputStream(bytes), format,
                bytes.length / format.getFrameSize())) {
            if (AudioSystem.write(input,
                    javax.sound.sampled.AudioFileFormat.Type.WAVE, output) <= 0) {
                throw new IOException("Không thể lưu bản ghi âm.");
            }
        }
        return output;
    }

    static void normalizePcm(byte[] bytes) throws IOException {
        int peak = 0;
        for (int index = 0; index + 1 < bytes.length; index += 2) {
            int sample = (short) ((bytes[index + 1] << 8) | (bytes[index] & 0xFF));
            peak = Math.max(peak, Math.abs(sample));
        }
        if (peak < 32) {
            throw new IOException(
                    "Tệp ghi âm gần như im lặng. Hãy kiểm tra micro và quyền truy cập.");
        }

        double gain = 24576.0 / peak;
        for (int index = 0; index + 1 < bytes.length; index += 2) {
            int sample = (short) ((bytes[index + 1] << 8) | (bytes[index] & 0xFF));
            int amplified = (int) Math.round(sample * gain);
            amplified = Math.max(Short.MIN_VALUE,
                    Math.min(Short.MAX_VALUE, amplified));
            bytes[index] = (byte) amplified;
            bytes[index + 1] = (byte) (amplified >> 8);
        }
    }

    public void cancel() {
        TargetDataLine activeLine;
        Thread activeThread;
        synchronized (this) {
            if (captureThread == null) {
                return;
            }
            recording = false;
            activeLine = line;
            activeThread = captureThread;
        }
        closeLine(activeLine);
        try {
            activeThread.join(2000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        synchronized (this) {
            if (!activeThread.isAlive() && captureThread == activeThread) {
                captureThread = null;
                line = null;
                captureFailure = null;
            }
        }
        if (activeThread.isAlive()) {
            System.err.println("Audio capture thread did not stop after cancellation.");
        }
    }

    private void capture(TargetDataLine activeLine) {
        byte[] buffer = new byte[4096];
        try {
            while (recording) {
                int count = activeLine.read(buffer, 0, buffer.length);
                if (count <= 0) {
                    continue;
                }
                synchronized (audio) {
                    int remaining = MAX_RECORDING_BYTES - audio.size();
                    if (count > remaining) {
                        audio.write(buffer, 0, Math.max(0, remaining));
                        captureFailure = new IOException(
                                "Bản ghi âm vượt quá giới hạn 16 MB.");
                        recording = false;
                        break;
                    }
                    audio.write(buffer, 0, count);
                }
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            if (recording) {
                captureFailure = new IOException(
                        "Không thể đọc dữ liệu từ micro.", exception);
            }
        } finally {
            recording = false;
            closeLine(activeLine);
        }
    }

    private synchronized void closeLine(TargetDataLine targetLine) {
        if (targetLine == null) {
            return;
        }
        if (targetLine.isOpen()) {
            if (targetLine.isRunning()) {
                targetLine.stop();
            }
            targetLine.close();
        }
    }
}
