package media;

import model.PacketType;
import org.jcodec.api.FrameGrab;
import org.jcodec.api.JCodecException;
import org.jcodec.api.PictureWithMetadata;
import org.jcodec.codecs.aac.AACDecoder;
import org.jcodec.common.Codec;
import org.jcodec.common.DemuxerTrack;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.AudioBuffer;
import org.jcodec.common.model.Packet;
import org.jcodec.containers.mp4.demuxer.MP4Demuxer;
import org.jcodec.scale.AWTUtil;
import ui.AppTheme;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Image;
import java.awt.Window;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class MediaAttachmentView extends JPanel {

    private final AudioPlayer audioPlayer;
    private final VideoPlayer videoPlayer;

    public MediaAttachmentView(Component parent, FileInfo info) {
        super(new BorderLayout(0, 8));
        if (info == null) {
            throw new IllegalArgumentException("Media information is required.");
        }
        setOpaque(true);
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.BORDER),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        setMaximumSize(new Dimension(380, 420));

        audioPlayer = info.getType() == PacketType.SOUND
                ? new AudioPlayer(info.getFile()) : null;
        videoPlayer = info.getType() == PacketType.VIDEO
                ? new VideoPlayer(info.getFile()) : null;
        if (audioPlayer != null) {
            add(audioPlayer, BorderLayout.CENTER);
        } else if (info.getType() == PacketType.IMAGE) {
            add(createImagePreview(parent, info), BorderLayout.CENTER);
        } else if (videoPlayer != null) {
            add(videoPlayer, BorderLayout.CENTER);
        } else {
            JLabel fileIcon = new JLabel("TỆP", SwingConstants.CENTER);
            fileIcon.setFont(new Font("Segoe UI", Font.BOLD, 24));
            fileIcon.setForeground(AppTheme.PRIMARY);
            fileIcon.setPreferredSize(new Dimension(100, 54));
            add(fileIcon, BorderLayout.CENTER);
        }

        JPanel footer = new JPanel(new BorderLayout(10, 0));
        footer.setOpaque(false);
        JPanel details = new JPanel(new BorderLayout(0, 3));
        details.setOpaque(false);
        JLabel filename = new JLabel(info.getFilename());
        filename.setFont(new Font("Segoe UI", Font.BOLD, 12));
        filename.setForeground(AppTheme.TEXT);
        filename.setToolTipText(info.getFilename());
        JLabel metadata = new JLabel(info.getTypeDisplayName() + " · "
                + info.getReadableSize());
        metadata.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        metadata.setForeground(AppTheme.MUTED);
        details.add(filename, BorderLayout.NORTH);
        details.add(metadata, BorderLayout.SOUTH);
        footer.add(details, BorderLayout.CENTER);

        JButton download = new JButton("↓ Tải xuống");
        download.setEnabled(info.getFile() != null && info.getFile().isFile());
        download.setToolTipText("Lưu bản sao vào thư mục đã chọn");
        download.addActionListener(event -> MediaManager.downloadMedia(parent, info));
        AppTheme.styleButton(download, false);
        footer.add(download, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);

        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0
                    && !isDisplayable()) {
                close();
            }
        });
    }

    public void close() {
        if (audioPlayer != null) {
            audioPlayer.close();
        }
        if (videoPlayer != null) {
            videoPlayer.close();
        }
    }

    private static JLabel createImagePreview(Component parent, FileInfo info) {
        JLabel preview = new JLabel("", SwingConstants.CENTER);
        preview.setOpaque(true);
        preview.setBackground(new Color(0xF2F4F7));
        preview.setPreferredSize(new Dimension(260, 160));
        javax.swing.ImageIcon thumbnail = MediaManager.loadThumbnail(info, 260);
        if (thumbnail == null) {
            preview.setText("Không thể xem trước ảnh");
        } else {
            preview.setIcon(thumbnail);
        }
        preview.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        preview.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                MediaManager.openMedia(parent, info);
            }
        });
        return preview;
    }

    private static final class AudioPlayer extends JPanel {

        private final File file;
        private final JButton playButton = new JButton("Nghe");
        private Clip clip;

        private AudioPlayer(File file) {
            super(new BorderLayout());
            this.file = file;
            setOpaque(false);
            setPreferredSize(new Dimension(260, 54));
            playButton.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            AppTheme.styleButton(playButton, false);
            playButton.addActionListener(event -> togglePlayback());
            add(playButton, BorderLayout.CENTER);
        }

        private void togglePlayback() {
            try {
                if (clip == null) {
                    if (file == null || !file.isFile()) {
                        throw new IOException("Không tìm thấy tệp ghi âm.");
                    }
                    clip = AudioSystem.getClip();
                    try (javax.sound.sampled.AudioInputStream stream =
                                 AudioSystem.getAudioInputStream(file)) {
                        AudioFormat format = stream.getFormat();
                        if (AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())
                                && format.getSampleSizeInBits() == 16
                                && !format.isBigEndian()) {
                            byte[] pcm = stream.readAllBytes();
                            AudioRecorder.normalizePcm(pcm);
                            clip.open(format, pcm, 0, pcm.length);
                        } else {
                            clip.open(stream);
                        }
                    }
                    clip.addLineListener(event -> {
                        if (event.getType() == LineEvent.Type.STOP
                                && clip != null
                                && clip.getFramePosition() >= clip.getFrameLength()) {
                            SwingUtilities.invokeLater(() -> {
                                clip.setFramePosition(0);
                                playButton.setText("Nghe");
                            });
                        }
                    });
                }
                if (clip.isRunning()) {
                    clip.stop();
                    playButton.setText("Nghe tiếp");
                } else {
                    if (clip.getFramePosition() >= clip.getFrameLength()) {
                        clip.setFramePosition(0);
                    }
                    clip.start();
                    playButton.setText("Tạm dừng");
                }
            } catch (LineUnavailableException | UnsupportedAudioFileException
                     | IOException exception) {
                JOptionPane.showMessageDialog(this,
                        "Không thể phát âm thanh: " + exception.getMessage(),
                        "Lỗi phát âm thanh", JOptionPane.ERROR_MESSAGE);
                close();
            }
        }

        private void close() {
            if (clip != null) {
                if (clip.isOpen()) {
                    if (clip.isRunning()) {
                        clip.stop();
                    }
                    clip.close();
                }
                clip = null;
            }
        }
    }

    private static final class VideoPlayer extends JPanel {

        private final File file;
        private final JLabel screen = new JLabel("Nhấn Phát để xem video",
                SwingConstants.CENTER);
        private final JButton playButton = new JButton("Phát");
        private final ScheduledExecutorService decoder =
                Executors.newSingleThreadScheduledExecutor(task -> {
                    Thread thread = new Thread(task, "InlineVideoPlayer");
                    thread.setDaemon(true);
                    return thread;
                });
        private final ExecutorService audioExecutor =
                Executors.newSingleThreadExecutor(task -> {
                    Thread thread = new Thread(task, "InlineVideoAudio");
                    thread.setDaemon(true);
                    return thread;
                });
        private final Object playbackLock = new Object();
        private volatile boolean playing;
        private volatile boolean closed;
        private volatile boolean videoFinished;
        private volatile boolean audioStarted;
        private volatile boolean audioErrorShown;
        private volatile long audioGeneration;
        private volatile SourceDataLine audioOutput;
        private volatile MP4Demuxer audioDemuxer;
        private volatile JDialog fullscreenDialog;
        private volatile JLabel fullscreenScreen;
        private SeekableByteChannel channel;
        private FrameGrab frameGrab;

        private VideoPlayer(File file) {
            super(new BorderLayout(0, 4));
            this.file = file;
            setOpaque(false);
            screen.setOpaque(true);
            screen.setBackground(new Color(0x111C2B));
            screen.setForeground(Color.WHITE);
            screen.setPreferredSize(new Dimension(320, 180));
            playButton.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            AppTheme.styleButton(playButton, false);
            playButton.addActionListener(event -> togglePlayback());
            add(screen, BorderLayout.CENTER);
            add(playButton, BorderLayout.SOUTH);
            screen.setCursor(java.awt.Cursor.getPredefinedCursor(
                    java.awt.Cursor.HAND_CURSOR));
            screen.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent event) {
                    if (event.getClickCount() == 2) {
                        showFullscreen();
                    }
                }
            });
            addHierarchyListener(event -> {
                if ((event.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0
                        && !isDisplayable()) {
                    close();
                }
            });
        }

        private void togglePlayback() {
            if (closed) {
                return;
            }
            if (playing) {
                playing = false;
                playButton.setText("Tiếp tục");
                SourceDataLine line = audioOutput;
                if (line != null) {
                    line.stop();
                }
                return;
            }
            if (videoFinished) {
                videoFinished = false;
                closeSource();
                stopAudioTrack();
            }
            playing = true;
            playButton.setText("Tạm dừng");
            synchronized (playbackLock) {
                playbackLock.notifyAll();
            }
            SourceDataLine line = audioOutput;
            if (line != null) {
                line.start();
            }
            startAudioTrack();
            decoder.execute(this::decodeNextFrame);
        }

        private void decodeNextFrame() {
            if (!playing || closed) {
                return;
            }
            try {
                if (frameGrab == null) {
                    if (file == null || !file.isFile()) {
                        throw new IOException("Không tìm thấy video.");
                    }
                    channel = NIOUtils.readableChannel(file);
                    frameGrab = FrameGrab.createFrameGrab(channel);
                }
                PictureWithMetadata frame = frameGrab.getNativeFrameWithMetadata();
                if (frame == null) {
                    playing = false;
                    videoFinished = true;
                    stopAudioTrack();
                    closeSource();
                    SwingUtilities.invokeLater(() -> playButton.setText("Phát lại"));
                    return;
                }
                Image image = AWTUtil.toBufferedImage(
                        frame.getPicture(), frame.getOrientation());
                SwingUtilities.invokeLater(() -> showFrame(image));
                long delay = Math.max(1L,
                        Math.round(Math.max(frame.getDuration(), 1.0 / 30.0) * 1000));
                decoder.schedule(this::decodeNextFrame, delay, TimeUnit.MILLISECONDS);
            } catch (IOException | JCodecException | RuntimeException exception) {
                playing = false;
                closeSource();
                SwingUtilities.invokeLater(() -> {
                    screen.setIcon(null);
                    screen.setText("Không thể phát định dạng video này");
                    playButton.setText("Thử lại");
                });
            }
        }

        private void startAudioTrack() {
            if (audioStarted || closed) {
                return;
            }
            audioStarted = true;
            long generation = ++audioGeneration;
            audioExecutor.execute(() -> playAudioTrack(generation));
        }

        private void playAudioTrack(long generation) {
            MP4Demuxer demuxer = null;
            SourceDataLine output = null;
            try {
                SeekableByteChannel audioChannel = NIOUtils.readableChannel(file);
                demuxer = MP4Demuxer.createMP4Demuxer(audioChannel);
                audioDemuxer = demuxer;
                List<DemuxerTrack> tracks = demuxer.getAudioTracks();
                if (tracks.isEmpty()) {
                    return;
                }
                DemuxerTrack track = tracks.get(0);
                if (!Codec.AAC.equals(track.getMeta().getCodec())) {
                    showAudioIssue("Định dạng âm thanh trong video chưa được hỗ trợ.");
                    return;
                }
                ByteBuffer codecData = track.getMeta().getCodecPrivate();
                if (codecData == null || !codecData.hasRemaining()) {
                    throw new IOException("Video không có cấu hình âm thanh hợp lệ.");
                }
                AACDecoder aacDecoder = new AACDecoder(codecData.duplicate());
                Packet packet = track.nextFrame();
                if (packet == null) {
                    return;
                }
                AudioBuffer audio = aacDecoder.decodeFrame(
                        packet.getData().duplicate(), ByteBuffer.allocate(0));
                org.jcodec.common.AudioFormat decodedFormat = audio.getFormat();
                if (decodedFormat.getSampleRate() <= 0
                        || decodedFormat.getChannels() <= 0
                        || decodedFormat.getSampleSizeInBits() <= 0) {
                    throw new IOException("Không đọc được định dạng âm thanh video.");
                }
                javax.sound.sampled.AudioFormat outputFormat =
                        new javax.sound.sampled.AudioFormat(
                                decodedFormat.getSampleRate(),
                                decodedFormat.getSampleSizeInBits(),
                                decodedFormat.getChannels(),
                                decodedFormat.isSigned(),
                                decodedFormat.isBigEndian());
                DataLine.Info lineInfo = new DataLine.Info(
                        SourceDataLine.class, outputFormat);
                output = (SourceDataLine) AudioSystem.getLine(lineInfo);
                output.open(outputFormat);
                audioOutput = output;

                while (packet != null && isAudioPlaybackCurrent(generation)) {
                    if (!awaitPlayback(generation)) {
                        break;
                    }
                    if (!output.isRunning()) {
                        output.start();
                    }
                    if (!writeAudioBuffer(output, audio, generation)) {
                        break;
                    }
                    packet = track.nextFrame();
                    if (packet != null) {
                        audio = aacDecoder.decodeFrame(
                                packet.getData().duplicate(), ByteBuffer.allocate(0));
                    }
                }
                if (packet == null && isAudioPlaybackCurrent(generation)) {
                    output.drain();
                }
            } catch (IOException | LineUnavailableException | RuntimeException exception) {
                if (isAudioPlaybackCurrent(generation)) {
                    showAudioIssue("Không thể phát âm thanh video: "
                            + exception.getMessage());
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                closeAudioOutput(output);
                if (demuxer != null) {
                    try {
                        demuxer.close();
                    } catch (IOException exception) {
                        System.err.println("Could not close video audio track: "
                                + exception.getMessage());
                    }
                }
                if (audioOutput == output) {
                    audioOutput = null;
                }
                if (audioDemuxer == demuxer) {
                    audioDemuxer = null;
                }
            }
        }

        private boolean writeAudioBuffer(SourceDataLine output, AudioBuffer audio,
                                         long generation) throws InterruptedException {
            ByteBuffer data = audio.getData().duplicate();
            byte[] pcm = new byte[data.remaining()];
            data.get(pcm);
            int offset = 0;
            while (offset < pcm.length && isAudioPlaybackCurrent(generation)) {
                if (!awaitPlayback(generation)) {
                    return false;
                }
                int written = output.write(pcm, offset, pcm.length - offset);
                if (written <= 0) {
                    return false;
                }
                offset += written;
            }
            return offset == pcm.length;
        }

        private boolean awaitPlayback(long generation) throws InterruptedException {
            synchronized (playbackLock) {
                while (isAudioPlaybackCurrent(generation) && !playing) {
                    playbackLock.wait();
                }
            }
            return isAudioPlaybackCurrent(generation);
        }

        private boolean isAudioPlaybackCurrent(long generation) {
            return !closed && generation == audioGeneration;
        }

        private void showAudioIssue(String message) {
            SwingUtilities.invokeLater(() -> {
                screen.setToolTipText(message);
                if (!audioErrorShown && !closed) {
                    audioErrorShown = true;
                    JOptionPane.showMessageDialog(this, message,
                            "Âm thanh video", JOptionPane.ERROR_MESSAGE);
                }
            });
        }

        private void stopAudioTrack() {
            audioGeneration++;
            audioStarted = false;
            synchronized (playbackLock) {
                playbackLock.notifyAll();
            }
            SourceDataLine output = audioOutput;
            closeAudioOutput(output);
            MP4Demuxer demuxer = audioDemuxer;
            if (demuxer != null) {
                try {
                    demuxer.close();
                } catch (IOException exception) {
                    System.err.println("Could not stop video audio track: "
                            + exception.getMessage());
                }
            }
        }

        private void closeAudioOutput(SourceDataLine output) {
            if (output == null) {
                return;
            }
            try {
                if (output.isOpen()) {
                    if (output.isRunning()) {
                        output.stop();
                    }
                    output.flush();
                    output.close();
                }
            } catch (IllegalStateException exception) {
                System.err.println("Could not stop video audio output: "
                        + exception.getMessage());
            }
        }

        private void showFrame(Image image) {
            if (closed) {
                return;
            }
            screen.setText("");
            screen.setIcon(new javax.swing.ImageIcon(scaleToFit(
                    image, screen.getWidth(), screen.getHeight())));
            JLabel expanded = fullscreenScreen;
            if (expanded != null) {
                expanded.setIcon(new javax.swing.ImageIcon(scaleToFit(
                        image, expanded.getWidth(), expanded.getHeight())));
            }
        }

        private void showFullscreen() {
            if (closed || fullscreenDialog != null) {
                return;
            }
            Window owner = SwingUtilities.getWindowAncestor(this);
            JDialog dialog = new JDialog(owner);
            dialog.setUndecorated(true);
            JLabel expanded = new JLabel("", SwingConstants.CENTER);
            expanded.setOpaque(true);
            expanded.setBackground(Color.BLACK);
            expanded.setToolTipText("Nhấn Esc hoặc nháy đúp để thoát toàn màn hình");
            expanded.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent event) {
                    if (event.getClickCount() == 2) {
                        closeFullscreen();
                    }
                }
            });
            dialog.getRootPane().registerKeyboardAction(
                    event -> closeFullscreen(),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                    javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
            fullscreenScreen = expanded;
            dialog.setContentPane(expanded);
            dialog.setBounds(getGraphicsConfiguration().getBounds());
            fullscreenDialog = dialog;
            dialog.setVisible(true);
            dialog.requestFocus();
            if (screen.getIcon() instanceof javax.swing.ImageIcon) {
                showFrame(((javax.swing.ImageIcon) screen.getIcon()).getImage());
            }
        }

        private void closeFullscreen() {
            JDialog dialog = fullscreenDialog;
            fullscreenDialog = null;
            fullscreenScreen = null;
            if (dialog != null) {
                dialog.dispose();
            }
        }

        private void closeSource() {
            frameGrab = null;
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException exception) {
                    System.err.println("Could not close video source: "
                            + exception.getMessage());
                }
                channel = null;
            }
        }

        private void close() {
            closed = true;
            playing = false;
            stopAudioTrack();
            closeFullscreen();
            decoder.shutdownNow();
            audioExecutor.shutdownNow();
            closeSource();
        }

        private static Image scaleToFit(Image image, int width, int height) {
            int targetWidth = Math.max(1, width);
            int targetHeight = Math.max(1, height);
            double ratio = Math.min((double) targetWidth / image.getWidth(null),
                    (double) targetHeight / image.getHeight(null));
            int scaledWidth = Math.max(1, (int) (image.getWidth(null) * ratio));
            int scaledHeight = Math.max(1, (int) (image.getHeight(null) * ratio));
            return image.getScaledInstance(
                    scaledWidth, scaledHeight, Image.SCALE_SMOOTH);
        }
    }
}
