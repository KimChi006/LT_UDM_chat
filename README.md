# LT_UDM_chat

Ứng dụng chat Java Swing hỗ trợ chat cá nhân, chat nhóm và gửi file, hình ảnh,
video, âm thanh.

## Yêu cầu

- JDK đã cài đặt (`java` và `javac` có trong `PATH`).
- Windows PowerShell.
- Gson đã có sẵn tại `ChatApp\lib\gson-2.14.0.jar`.

Kiểm tra JDK:

```powershell
java -version
javac -version
```

**Không chạy Gson như một ứng dụng riêng.** `gson-2.14.0.jar` là thư viện mà
ứng dụng cần khi biên dịch và chạy; script bên dưới tự thêm thư viện này vào
classpath.

## Chạy lần đầu bằng PowerShell

Mở PowerShell tại thư mục gốc repo (`LT_UDM_chat`).

1. Mở terminal thứ nhất, khởi động server:

   ```powershell
   .\run.ps1 -Mode server
   ```

   Đợi đến khi thấy `Server started on port 8080`. Giữ terminal này mở trong
   lúc sử dụng ứng dụng; nhấn `Ctrl+C` để dừng server.

2. Mở terminal thứ hai, khởi động ứng dụng:

   ```powershell
   .\run.ps1 -Mode app
   ```

   Lần đầu, chọn **Create account** để tạo tài khoản, sau đó đăng nhập. Tìm
   người trong danh sách hoặc ô tìm kiếm và chọn **Nhắn tin**. Để thử chat hai
   người trên cùng máy, mở ứng dụng thêm lần nữa và đăng nhập bằng tài khoản
   khác.

Script sẽ biên dịch mã Java vào `ChatApp\bin` trước khi chạy. Nếu chỉ muốn
biên dịch mà chưa mở ứng dụng:

```powershell
.\run.ps1 -Mode build
```

## Tạo và dùng nhóm

Server phải đang chạy và cần có ít nhất hai tài khoản. Trong ứng dụng, chọn
**Tạo nhóm**, nhập tên rồi chọn thành viên. Nhóm sẽ hiện ở mục **Nhóm** cùng
với tên người tạo và danh sách thành viên. Chọn **Mở nhóm chat** để nhắn tin.
Nhóm được lưu trong `ChatApp\data\groups.json`.

Có thể chạy công cụ nhóm cũ bằng:

```powershell
.\run.ps1 -Mode group
```

Media trong cuộc trò chuyện có nút **Media (n)**; chọn file trong danh sách để
mở lại file cụ thể, thay vì chỉ mở file gửi/nhận gần nhất.

Trong chat cá nhân và chat nhóm, nút **Emoji** mở bảng chọn có hai thẻ:
**Emoji** chèn biểu tượng vào ô nhập, còn **Sticker** gửi biểu tượng đã chọn
ngay thành một tin nhắn. Ứng dụng dùng ảnh emoji có sẵn trong
`ChatApp\resources\emoji` để hiển thị trong nội dung chat và lịch sử, tránh
phụ thuộc vào font emoji trên máy nhận.

Khi đang ở màn hình chính, ứng dụng giữ kết nối với server để nhận tin nhắn
riêng và nhóm; tin đến được lưu vào lịch sử để mở xem sau. Khi mở một cuộc trò
chuyện, cửa sổ chat tiếp quản kết nối; khi đóng cửa sổ, kết nối màn hình chính
được khôi phục.

## Các chế độ của `run.ps1`

| Lệnh | Tác dụng |
| --- | --- |
| `.\run.ps1 -Mode build` | Biên dịch ứng dụng, không mở cửa sổ |
| `.\run.ps1 -Mode server` | Khởi chạy server tại cổng 8080 |
| `.\run.ps1 -Mode app` | Mở màn hình đăng nhập và giao diện chính |
| `.\run.ps1 -Mode chat` | Mở nhanh chat cá nhân để thử nghiệm |
| `.\run.ps1 -Mode group` | Mở công cụ tạo/mở chat nhóm |
| `.\run.ps1 -Mode media-demo` | Mở cửa sổ thử gửi media độc lập |

Mọi chế độ đều tự biên dịch trước khi chạy và tự nạp Gson.

## Mở từ VS Code

Mở thư mục gốc repo trong VS Code. Workspace đã cấu hình source path và
`ChatApp\lib\gson-2.14.0.jar` trong `.vscode\settings.json`. Các lớp chạy chính:

- `server.ServerMain` — server, chạy lớp này trước.
- `auth.LoginUI` — đăng nhập, sau đó mở giao diện chính.
- `chat.ChatHomeUI` — tìm người, tạo nhóm và mở cuộc trò chuyện.
- `chat.ChatUI` — cửa sổ chat cá nhân.
- `group.GroupChatUI` — cửa sổ chat nhóm.

Nếu VS Code vẫn báo `The import com.google.gson cannot be resolved`, mở
Command Palette và chạy **Java: Clean Java Language Server Workspace**, sau đó
reload VS Code.

## Dữ liệu và media

- Dữ liệu người dùng, nhóm và lịch sử chat nằm trong `ChatApp\data`.
- Media tải về nằm trong `ChatApp\media` khi chạy từ thư mục gốc repo hoặc từ
  thư mục `ChatApp`.
- Mỗi file gửi tối đa 50 MB.
