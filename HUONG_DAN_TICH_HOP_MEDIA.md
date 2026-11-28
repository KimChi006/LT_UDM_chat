# Hướng dẫn tích hợp module Media (gửi cho TV2)

Module media nằm ở `src/media`. TV2 chỉ cần dùng class `MediaManager`.

ChatUI và GroupChatUI hiện đã tích hợp sẵn nút Attach, nhận media, lưu vào
lịch sử và mở media gần nhất. Các đoạn ví dụ dưới đây dùng khi tích hợp vào
một cửa sổ chat khác.

## Chat cá nhân (ChatUI)

```java
// field
private MediaManager mediaManager;

// trong connectToServer(), sau khi tạo clientSocket và gửi LOGIN
mediaManager = new MediaManager(clientSocket, username,
        new MediaManager.MediaListener() {
            @Override
            public void onMediaReceived(FileInfo info) {
                SwingUtilities.invokeLater(() ->
                    appendMessage(info.getSender() + " sent " + info
                            + " (" + info.getFilepath() + ")"));
            }
            @Override
            public void onMediaSent(FileInfo info) {
                SwingUtilities.invokeLater(() ->
                    appendMessage(username + " sent " + info));
            }
            @Override
            public void onMediaError(String message) {
                SwingUtilities.invokeLater(() ->
                    appendMessage("[System] " + message));
            }
        });

// nút "Attach" (thêm vào inputPanel)
JButton attachButton = new JButton("Attach");
attachButton.addActionListener(e -> {
    File file = MediaManager.chooseFile(this);
    if (file != null) {
        mediaManager.sendMedia(receiver, null, file);
    }
});

// dòng đầu tiên của onPacketReceived(String packet)
if (mediaManager.handlePacket(packet)) {
    return;
}

// trong disconnect()
if (mediaManager != null) {
    mediaManager.shutdown();
}
```

## Chat nhóm (GroupChatUI)

Giống trên, chỉ khác 2 chỗ:

```java
mediaManager.sendMedia(null, group.getGroupId(), file);
if (mediaManager.handlePacket(packet, group.getGroupId())) { return; }
```

## Mở / xem media đã nhận

```java
MediaManager.openMedia(this, info);          // ảnh: cửa sổ xem, video/âm thanh/file: mở bằng chương trình mặc định
ImageIcon icon = MediaManager.loadThumbnail(info, 200); // ảnh nhỏ để hiện trong chat (cần JTextPane/JLabel)
```

## Định dạng packet (cho TV1 và TV4 biết)

```
{"type":"IMAGE","sender":"chi","receiver":"nam","transferid":"...",
 "filename":"a.png","filesize":"20480","totalchunks":"1","chunkindex":"0","data":"<base64>"}
```
- type là FILE, IMAGE, VIDEO hoặc SOUND (không thêm PacketType mới).
- Chat nhóm: `receiver` rỗng, có thêm `"groupId":"..."`.
- File lớn được cắt chunk 32KB, mỗi chunk là một dòng. Tối đa 50MB.
- File nhận về lưu ở `media/files|images|videos|sounds/`.
- TV4 lưu lịch sử: dùng `info.getFilename()` làm content và `info.getFilepath()` làm filepath.
- Server (TV1) không cần sửa gì.

## Khắc phục lỗi import Gson trong VS Code

Thư viện đã được cung cấp tại `ChatApp/lib/gson-2.14.0.jar`. Workspace cần có
`java.project.referencedLibraries` trỏ đến `ChatApp/lib/**/*.jar` (đã cấu hình
trong `.vscode/settings.json`). Nếu VS Code vẫn báo `The import
com.google.gson cannot be resolved`, mở Command Palette và chạy **Java: Clean
Java Language Server Workspace**, sau đó reload VS Code. Nếu tự biên dịch bằng
terminal từ thư mục gốc repo, thêm jar vào classpath:

```powershell
javac -encoding UTF-8 -cp "ChatApp\lib\gson-2.14.0.jar" -d ChatApp\bin (Get-ChildItem ChatApp\src -Recurse -Filter *.java | ForEach-Object FullName)
```
